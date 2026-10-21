#!/usr/bin/env python3

import io
import json
import os
from pathlib import Path
import queue
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from probe_katago_focus import (QUICK_ANALYSIS_MOVES, GtpProbe, analysis_timing, parse_analysis, probe,
                                quick_analysis_within_budget, sha256)

# Stands in for `katago analysis`: answers each query in order, after FAKE_CONFIGURED_DELAY seconds
# unless the batch size is overridden to 2, and records its arguments and queries.
FAKE_ANALYSIS_ENGINE = '''
import json, os, sys, time
arguments = sys.argv[1:]
overrides = arguments[arguments.index("-override-config") + 1]
delay = 0.0 if "nnMaxBatchSize=2" in overrides else float(os.environ["FAKE_CONFIGURED_DELAY"])
with open(os.environ["FAKE_RECORD"], "a", encoding="utf-8") as record:
    record.write(json.dumps({"arguments": arguments}) + "\\n")
    for line in sys.stdin:
        query = json.loads(line)
        record.write(json.dumps({"query": query}) + "\\n")
        if os.environ.get("FAKE_REJECT"):
            print(json.dumps({"id": query["id"], "error": "illegal move"}), flush=True)
            continue
        time.sleep(delay)
        print(json.dumps({"id": query["id"], "turnNumber": len(query["moves"]), "moveInfos": []}), flush=True)
'''


class FocusProbeTest(unittest.TestCase):
    def test_onnx_provider_cannot_inject_configuration_or_silently_use_another_backend(self):
        with self.assertRaisesRegex(ValueError, "execution provider"):
            probe(Path("unused"), Path("unused"), Path("unused"), onnx_provider="cpu\nrules=bad")
        with tempfile.TemporaryDirectory() as root:
            path = Path(root)
            engine, model = path / "engine", path / "model"
            engine.write_bytes(b"engine")
            model.write_bytes(b"model")
            from probe_katago_focus import SOURCE_COMMIT
            with patch("probe_katago_focus.subprocess.run") as command:
                command.return_value.stdout = f"Git revision: {SOURCE_COMMIT}\nUsing Eigen(CPU) backend\n"
                with self.assertRaisesRegex(ValueError, "non-ONNX"):
                    probe(engine, model, path / "evidence", onnx_provider="cpu")
            result = json.loads((path / "evidence/result.json").read_text())
            self.assertEqual("cpu", result["executionProvider"])
            self.assertEqual("FAIL", result["status"])

    def fake_session(self, lines):
        session = object.__new__(GtpProbe)
        session.timeout = 0.01
        session.log = io.StringIO()
        session.lines = queue.Queue()
        for line in lines:
            session.lines.put(line)
        session.command = lambda command: None
        return session

    def test_root_visits_are_not_sum_of_candidates(self):
        self.assertEqual(
            (15, {"D4": 100, "Q16": 40}),
            parse_analysis("info move D4 visits 100 info move Q16 visits 40 rootInfo visits 15"),
        )

    def test_non_analysis_is_ignored(self):
        self.assertIsNone(parse_analysis("=14 KataGo"))

    def test_missing_candidates_fail(self):
        with self.assertRaises(ValueError):
            parse_analysis("rootInfo visits 30")

    def test_waits_for_focused_candidate_and_normal_tree_progress(self):
        session = self.fake_session([
            "info move D4 visits 0 rootInfo visits 12",
            "info move D4 visits 5 rootInfo visits 23",
        ])
        self.assertEqual(23, session.analysis("focus", 10, ("D4",))["rootVisits"])

    def test_tree_reset_is_failure_even_if_later_output_recovers(self):
        session = self.fake_session([
            "info move D4 visits 5 rootInfo visits 5",
            "info move D4 visits 50 rootInfo visits 100",
        ])
        with self.assertRaisesRegex(RuntimeError, "search tree reset"):
            session.analysis("focus", 40, ("D4",))

    def test_exit_before_result_fails(self):
        with self.assertRaisesRegex(RuntimeError, "exited"):
            self.fake_session([None]).analysis("focus", 0, ())

    def test_no_output_times_out(self):
        with self.assertRaises(TimeoutError):
            self.fake_session([]).analysis("focus", 0, ())

    def test_never_overwrites_evidence(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root)
            engine = path / "engine"
            model = path / "model"
            engine.write_bytes(b"engine")
            model.write_bytes(b"model")
            with self.assertRaises(FileExistsError):
                probe(engine, model, path)

    def test_rejects_binary_from_another_commit(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root)
            engine = path / "engine"
            model = path / "model"
            engine.write_bytes(b"engine")
            model.write_bytes(b"model")
            with patch("probe_katago_focus.subprocess.run") as command:
                command.return_value.stdout = "KataGo v1.18.2\nGit revision: old\n"
                with self.assertRaisesRegex(ValueError, "source commit"):
                    probe(engine, model, path / "evidence")
            self.assertIn('"status": "FAIL"', (path / "evidence/result.json").read_text())

    def test_version_check_uses_explicit_deadline_without_retrying_timeout(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root)
            engine, model = path / "engine", path / "model"
            engine.write_bytes(b"engine")
            model.write_bytes(b"model")
            with patch("probe_katago_focus.subprocess.run") as command:
                command.side_effect = subprocess.TimeoutExpired("version", 87)
                with self.assertRaises(subprocess.TimeoutExpired):
                    probe(engine, model, path / "evidence", timeout=87)
                self.assertEqual(1, command.call_count)
                self.assertEqual(87, command.call_args.kwargs["timeout"])
            result = json.loads((path / "evidence/result.json").read_text())
            self.assertEqual("FAIL", result["status"])
            self.assertEqual([], result["steps"])
            self.assertEqual(87, result["operationTimeoutSeconds"])
            self.assertGreaterEqual(result["versionCheckSeconds"], 0)

    def test_invalid_deadline_cannot_disable_timeouts(self):
        for timeout in (0, -1, 601, float("nan"), float("inf")):
            with self.assertRaisesRegex(ValueError, "timeout"):
                probe(Path("unused-engine"), Path("unused-model"), Path("unused-evidence"), timeout)

    def test_quick_analysis_budget_tolerates_noise_but_not_padding(self):
        def within(configured, reference):
            return quick_analysis_within_budget({"steadySecondsPerPosition": configured},
                                                {"steadySecondsPerPosition": reference})
        # The first Metal patch on an M4 Pro, padding batches of one or two positions to 64 rows.
        self.assertFalse(within(2.7, 0.05))
        self.assertTrue(within(0.06, 0.05))
        # Six times slower, but under a second for the whole run: timer noise on a fast host.
        self.assertTrue(within(0.03, 0.005))

    def run_analysis_timing(self, configured_delay, reject=False, onnx_provider=None):
        root = Path(self.enterContext(tempfile.TemporaryDirectory()))
        fake, record, config = root / "fake_katago.py", root / "record.jsonl", root / "analysis.cfg"
        fake.write_text(FAKE_ANALYSIS_ENGINE, encoding="utf-8")
        config.write_text("nnMaxBatchSize = 64\n", encoding="utf-8")
        real_popen = subprocess.Popen

        def launch(command, **kwargs):
            return real_popen([sys.executable, str(fake)] + command[1:], **kwargs)

        environment = {"FAKE_CONFIGURED_DELAY": str(configured_delay), "FAKE_RECORD": str(record)}
        if reject:
            environment["FAKE_REJECT"] = "1"
        with patch.dict(os.environ, environment), \
                patch("probe_katago_focus.subprocess.Popen", side_effect=launch), \
                patch("probe_katago_focus.MIN_SLOWDOWN_SECONDS", 0.2):
            timing = analysis_timing(root / "katago", root / "model.bin.gz", config, root, 10, onnx_provider)
        rows = [json.loads(line) for line in record.read_text(encoding="utf-8").splitlines()]
        return timing, rows, config

    def test_quick_analysis_with_the_shipped_config_is_timed_against_a_batch_of_two(self):
        timing, rows, config = self.run_analysis_timing(0.0)
        self.assertEqual("PASS", timing["status"])
        self.assertEqual(sha256(config), timing["configSha256"])
        self.assertEqual((40, 2), (timing["positions"], timing["maxVisits"]))
        launches = [row["arguments"] for row in rows if "arguments" in row]
        self.assertEqual(2, len(launches))
        for arguments in launches:
            self.assertEqual("analysis", arguments[0])
            self.assertEqual(str(config), arguments[arguments.index("-config") + 1])
        reference, configured = (arguments[arguments.index("-override-config") + 1] for arguments in launches)
        self.assertEqual(
            "analysisPVLen=15,numSearchThreads=,numSearchThreadsPerAnalysisThread=1", configured)
        self.assertEqual(configured + ",nnMaxBatchSize=2", reference)
        queries = [row["query"] for row in rows if "query" in row]
        self.assertEqual(80, len(queries))
        self.assertEqual([["B", "R16"], ["W", "D17"], ["B", "Q3"]], queries[2]["moves"])
        self.assertEqual(list(QUICK_ANALYSIS_MOVES), [move for _, move in queries[39]["moves"]])
        self.assertEqual({2}, {query["maxVisits"] for query in queries})

    def test_padding_the_shipped_batch_size_fails_the_probe_with_its_numbers(self):
        timing, _, _ = self.run_analysis_timing(0.02)
        self.assertEqual("FAIL", timing["status"])
        self.assertGreaterEqual(timing["configured"]["steadySecondsPerPosition"], 0.02)
        self.assertLess(timing["reference"]["steadySecondsPerPosition"], 0.02)

    def test_onnx_provider_reaches_the_analysis_engine_as_the_app_launches_it(self):
        timing, rows, _ = self.run_analysis_timing(0.0, onnx_provider="openvino")
        self.assertTrue(timing["overrides"].endswith(",onnxProvider=openvino"))
        self.assertTrue(all("onnxProvider=openvino" in row["arguments"][-1] for row in rows if "arguments" in row))
        # DirectML pads every batch, so the app runs quick analysis with a batch its two threads fill.
        timing, rows, _ = self.run_analysis_timing(0.0, onnx_provider="directml")
        self.assertTrue(timing["overrides"].endswith(",onnxProvider=directml,nnMaxBatchSize=2"))
        configured = [row["arguments"] for row in rows if "arguments" in row][1]
        self.assertTrue(configured[configured.index("-override-config") + 1].endswith(
            "onnxProvider=directml,nnMaxBatchSize=2"))

    def test_a_rejected_quick_analysis_query_fails(self):
        with self.assertRaisesRegex(RuntimeError, "rejected a reference quick-analysis query"):
            self.run_analysis_timing(0.0, reject=True)

    def test_digest_is_of_actual_bytes(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "engine"
            path.write_bytes(b"abc")
            self.assertEqual(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                sha256(path),
            )


if __name__ == "__main__":
    unittest.main()
