#!/usr/bin/env python3
"""Exercise same-tree GTP focus against a real, explicitly selected KataGo process, then time the
app's whole-game quick analysis with the shipped analysis config."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import queue
import re
import subprocess
import threading
import time


from katago_source_targets import SOURCE_COMMIT
ROOT_VISITS = re.compile(r"\brootInfo visits (\d+)\b")
CANDIDATE_VISITS = re.compile(r"\binfo move ([A-T][0-9]+|pass) visits (\d+)\b")
# The app's automatic whole-game quick analysis, as KataGoRuntimeHelper starts it for few visits:
# the shipped analysis.cfg (KataGo's analysis_example.cfg, nnMaxBatchSize = 64) with these
# overrides, two analysis threads, and one 2-visit query per position, all sent at once.
QUICK_ANALYSIS_OVERRIDES = "analysisPVLen=15,numSearchThreads=,numSearchThreadsPerAnalysisThread=1"
QUICK_ANALYSIS_VISITS = 2
# The first 40 moves of the ear-reddening game (1846), Black first.
QUICK_ANALYSIS_MOVES = tuple(
    "R16 D17 Q3 P17 C4 C14 E4 R5 Q15 O4 Q5 Q4 P4 R4 P3 P5 Q6 R3 O3 P6 "
    "Q7 P7 Q8 N4 N3 P8 Q9 M3 M2 L2 M4 L3 R2 S2 S1 N2 O2 Q2 Q1 R1".split())
# The shipped config may cost quick analysis at most this factor over a batch of 2. The first Metal
# patch padded every batch of one or two positions to the configured 64 rows, about 50 times.
MAX_SLOWDOWN = 3.0
# Less extra time than this for the whole run is accepted whatever the ratio: on a fast host the
# per-position times are milliseconds, and their ratio is timer noise.
MIN_SLOWDOWN_SECONDS = 2.0


def sha256(path: Path) -> str:
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def parse_analysis(line: str) -> tuple[int, dict[str, int]] | None:
    root = ROOT_VISITS.search(line)
    if root is None:
        return None
    moves = {move: int(visits) for move, visits in CANDIDATE_VISITS.findall(line)}
    if not moves:
        raise ValueError("rootInfo without candidate visit statistics")
    return int(root.group(1)), moves


class GtpProbe:
    def __init__(self, executable: Path, model: Path, evidence: Path, timeout: float,
                 onnx_provider: str | None = None):
        self.timeout = timeout
        self.sequence = 0
        self.lines: queue.Queue[str | None] = queue.Queue()
        self.log = (evidence / "protocol.log").open("w", encoding="utf-8")
        config = evidence / "gtp.cfg"
        config.write_text(
            "logAllGTPCommunication = false\nlogSearchInfo = false\nlogToStderr = true\n"
            "numSearchThreads = 2\nrules = chinese\nponderingEnabled = false\n"
            + (f"onnxProvider = {onnx_provider}\n" if onnx_provider else ""),
            encoding="utf-8",
        )
        self.stderr = (evidence / "stderr.log").open("w", encoding="utf-8")
        self.process = subprocess.Popen(
            [str(executable), "gtp", "-model", str(model), "-config", str(config)],
            cwd=evidence,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=self.stderr,
            text=True,
            encoding="utf-8",
        )
        self.reader = threading.Thread(target=self._read, daemon=True)
        self.reader.start()

    def _read(self) -> None:
        try:
            assert self.process.stdout is not None
            for line in self.process.stdout:
                self.lines.put(line.rstrip("\r\n"))
        finally:
            self.lines.put(None)

    def _next(self, deadline: float) -> str:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError("KataGo GTP response timed out")
        try:
            line = self.lines.get(timeout=remaining)
        except queue.Empty as error:
            raise TimeoutError("KataGo GTP response timed out") from error
        if line is None:
            raise RuntimeError("KataGo exited before the expected GTP response")
        self.log.write("< " + line + "\n")
        self.log.flush()
        return line

    def command(self, command: str) -> None:
        self.sequence += 1
        numbered = f"{self.sequence} {command}"
        self.log.write("> " + numbered + "\n")
        self.log.flush()
        assert self.process.stdin is not None
        self.process.stdin.write(numbered + "\n")
        self.process.stdin.flush()
        deadline = time.monotonic() + self.timeout
        while True:
            line = self._next(deadline)
            if re.match(rf"^\?{self.sequence}(?:\s|$)", line):
                raise RuntimeError("KataGo rejected " + command + ": " + line)
            if re.match(rf"^={self.sequence}(?:\s|$)", line):
                return

    def analysis(self, command: str, previous: int, watched: tuple[str, ...]) -> dict:
        self.command(command)
        deadline = time.monotonic() + self.timeout
        while True:
            parsed = parse_analysis(self._next(deadline))
            if parsed is None:
                continue
            root, moves = parsed
            if root < previous:
                raise RuntimeError(f"search tree reset: root visits {previous} -> {root}")
            if root >= previous + 10 and all(moves.get(move, 0) > 0 for move in watched):
                return {"command": command, "rootVisits": root, "candidateVisits": moves}

    def close(self) -> None:
        try:
            if self.process.poll() is None:
                self.process.terminate()
                try:
                    self.process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    self.process.kill()
                    self.process.wait(timeout=5)
            self.reader.join(timeout=5)
            if self.process.stdin:
                self.process.stdin.close()
            if self.process.stdout:
                self.process.stdout.close()
        finally:
            self.log.close()
            self.stderr.close()


def quick_analysis(executable: Path, model: Path, config: Path, evidence: Path, timeout: float,
                   overrides: str, label: str) -> dict:
    """Times one run of the app's quick analysis in a new analysis-engine process.

    The first result includes loading the model and compiling, so the run is measured by the time
    per position after it. `timeout` bounds the wait for each result.
    """
    lines: queue.Queue[str | None] = queue.Queue()
    finished: list[float] = []
    with (evidence / f"analysis-{label}.log").open("w", encoding="utf-8") as stderr:
        process = subprocess.Popen(
            [str(executable), "analysis", "-model", str(model), "-config", str(config),
             "-quit-without-waiting", "-override-config", overrides],
            cwd=evidence,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=stderr,
            text=True,
            encoding="utf-8",
        )

        def read() -> None:
            try:
                assert process.stdout is not None
                for line in process.stdout:
                    lines.put(line)
            finally:
                lines.put(None)

        reader = threading.Thread(target=read, daemon=True)
        reader.start()
        try:
            assert process.stdin is not None
            pending = set()
            started = time.monotonic()
            for turn in range(1, len(QUICK_ANALYSIS_MOVES) + 1):
                pending.add(f"{label}-{turn}")
                moves = [["BW"[i % 2], move] for i, move in enumerate(QUICK_ANALYSIS_MOVES[:turn])]
                process.stdin.write(json.dumps({
                    "id": f"{label}-{turn}", "moves": moves, "rules": "chinese", "komi": 7.5,
                    "boardXSize": 19, "boardYSize": 19, "maxVisits": QUICK_ANALYSIS_VISITS,
                }) + "\n")
            process.stdin.flush()
            while pending:
                try:
                    line = lines.get(timeout=timeout)
                except queue.Empty as error:
                    raise TimeoutError(f"KataGo {label} quick analysis timed out") from error
                if line is None:
                    raise RuntimeError(f"KataGo exited during the {label} quick analysis")
                response = json.loads(line)
                if "error" in response:
                    raise RuntimeError(f"KataGo rejected a {label} quick-analysis query: {response['error']}")
                if response.get("id") in pending and not response.get("isDuringSearch"):
                    pending.remove(response["id"])
                    finished.append(time.monotonic() - started)
            process.stdin.close()
            if process.wait(timeout=10) != 0:
                raise RuntimeError(f"KataGo did not exit cleanly after the {label} quick analysis")
        finally:
            if process.poll() is None:
                process.kill()
                process.wait(timeout=5)
            reader.join(timeout=5)
            for stream in (process.stdin, process.stdout):
                try:
                    if stream:
                        stream.close()
                except OSError:
                    pass
    first, last = finished[0], finished[-1]
    return {"overrides": overrides, "firstResultSeconds": round(first, 3),
            "allResultsSeconds": round(last, 3),
            "steadySecondsPerPosition": round((last - first) / (len(finished) - 1), 4)}


def quick_analysis_within_budget(configured: dict, reference: dict) -> bool:
    """Whether the shipped config costs quick analysis at most MAX_SLOWDOWN over a batch of 2."""
    slow = configured["steadySecondsPerPosition"]
    fast = reference["steadySecondsPerPosition"]
    extra = (slow - fast) * (len(QUICK_ANALYSIS_MOVES) - 1)
    return slow <= MAX_SLOWDOWN * fast or extra <= MIN_SLOWDOWN_SECONDS


def quick_analysis_overrides(onnx_provider: str | None) -> str:
    """The overrides the app's quick analysis runs an engine with this ONNX provider under."""
    overrides = QUICK_ANALYSIS_OVERRIDES + (f",onnxProvider={onnx_provider}" if onnx_provider else "")
    if onnx_provider == "directml":
        # DirectML pads every batch, so AnalysisEngineLaunchCommand sizes it for the two
        # quick-analysis threads instead of the config's 64.
        overrides += ",nnMaxBatchSize=2"
    return overrides


def analysis_timing(executable: Path, model: Path, config: Path, evidence: Path, timeout: float,
                    onnx_provider: str | None = None) -> dict:
    """Times the app's quick analysis with the shipped analysis config against a batch of 2.

    The GTP checks never use the analysis config, so they missed the first Metal patch padding every
    quick-analysis batch to the config's nnMaxBatchSize. Comparing against the same engine with
    nnMaxBatchSize = 2 keeps the judgement independent of how fast the host is.
    """
    provider = f",onnxProvider={onnx_provider}" if onnx_provider else ""
    overrides = quick_analysis_overrides(onnx_provider)
    reference = quick_analysis(executable, model, config, evidence, timeout,
                               QUICK_ANALYSIS_OVERRIDES + provider + ",nnMaxBatchSize=2", "reference")
    configured = quick_analysis(executable, model, config, evidence, timeout, overrides, "configured")
    return {
        "status": "PASS" if quick_analysis_within_budget(configured, reference) else "FAIL",
        "configSha256": sha256(config),
        "overrides": overrides,
        "positions": len(QUICK_ANALYSIS_MOVES),
        "maxVisits": QUICK_ANALYSIS_VISITS,
        "reference": reference,
        "configured": configured,
    }


def probe(executable: Path, model: Path, evidence: Path, timeout: float = 120,
          onnx_provider: str | None = None, analysis_config: Path | None = None) -> dict:
    if not 1 <= timeout <= 600:
        raise ValueError("timeout must be between 1 and 600 seconds")
    if onnx_provider not in {None, "cpu", "directml", "openvino"}:
        raise ValueError("Unsupported ONNX execution provider")
    executable, model, evidence = executable.resolve(), model.resolve(), evidence.resolve()
    if not executable.is_file() or not model.is_file():
        raise ValueError("explicit executable and model files are required")
    if analysis_config is not None:
        analysis_config = analysis_config.resolve()
        if not analysis_config.is_file():
            raise ValueError("the analysis config must be an existing file")
    evidence.mkdir(parents=True, exist_ok=False)
    result = {"status": "FAIL", "sourceCommit": SOURCE_COMMIT, "steps": [],
              "executionProvider": onnx_provider or "native"}
    session = None
    try:
        # A first macOS launch can wait for OS security scanning before main().
        # Use the explicit operation deadline, with no retries or failure reclassification.
        version_started = time.monotonic()
        try:
            version = subprocess.run(
                [str(executable), "version"], check=True, capture_output=True, text=True,
                timeout=timeout,
            ).stdout
        finally:
            result["versionCheckSeconds"] = time.monotonic() - version_started
            result["operationTimeoutSeconds"] = timeout
        if f"Git revision: {SOURCE_COMMIT}" not in version:
            raise ValueError("binary does not report the pinned upstream source commit")
        if onnx_provider and "Using ONNX Runtime backend" not in version:
            raise ValueError("Requested ONNX provider on a non-ONNX engine")
        result.update(version=version, executableSha256=sha256(executable), modelSha256=sha256(model))
        session = GtpProbe(executable, model, evidence, timeout, onnx_provider)
        for command in ("boardsize 19", "komi 7.5", "play B D4", "play W Q16"):
            session.command(command)
        base = "kata-analyze B interval 10 rootInfo true minmoves 20"
        previous = 0
        for suffix, watched in (
            ("", ()),
            (" focus Q4 0.5", ("Q4",)),
            (" focus Q4,D16 0.5", ("Q4", "D16")),
            (" focus D16 0.5", ("D16",)),
            ("", ()),
        ):
            step = session.analysis(base + suffix, previous, watched)
            result["steps"].append(step)
            previous = step["rootVisits"]
        session.command("name")
        session.command("quit")
        if session.process.wait(timeout=10) != 0:
            raise RuntimeError("KataGo did not exit cleanly")
        session.close()
        session = None
        if analysis_config is not None:
            timing = analysis_timing(executable, model, analysis_config, evidence, timeout, onnx_provider)
            result["analysis"] = timing
            if timing["status"] != "PASS":
                raise RuntimeError(
                    "the shipped analysis config slows quick analysis down: "
                    f"{timing['configured']['steadySecondsPerPosition']} s per position against "
                    f"{timing['reference']['steadySecondsPerPosition']} s with a batch of 2")
        result["status"] = "PASS"
        return result
    except Exception as error:
        result["error"] = str(error)
        raise
    finally:
        if session is not None:
            session.close()
        (evidence / "result.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--engine", type=Path, required=True)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--timeout", type=float, default=120)
    parser.add_argument("--onnx-provider", choices=("cpu", "directml", "openvino"))
    parser.add_argument("--analysis-config", type=Path, required=True,
                        help="the analysis config that ships with the engine "
                             "(the pinned source's cpp/configs/analysis_example.cfg)")
    args = parser.parse_args()
    if not 1 <= args.timeout <= 600:
        parser.error("timeout must be between 1 and 600 seconds")
    result = probe(args.engine, args.model, args.evidence, args.timeout, args.onnx_provider,
                   args.analysis_config)
    timing = result["analysis"]
    print(json.dumps({
        "status": result["status"], "rootVisits": [s["rootVisits"] for s in result["steps"]],
        "quickAnalysisSecondsPerPosition": {
            label: timing[label]["steadySecondsPerPosition"] for label in ("reference", "configured")},
    }))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
