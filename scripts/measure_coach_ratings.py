#!/usr/bin/env python3
"""How often AI 陪练's 边下边提醒 rates a move 缓手, 问题手 or 恶手, for players of each rank.

Both sides are stand-ins for players of the rank: every move is drawn from KataGo's HumanSL policy
for that rank. Black's moves are judged the way the app judges the player's, with the same review
budgets (docs/ai-coach.md). `Review`, `judge` and `verdict` mirror PositionReview, MoveFeedback and
MoveVerdict; change them together.

Play and record; a game takes about five minutes on an Apple Silicon Mac:

    python3 scripts/measure_coach_ratings.py play --katago katago --model kata1-b11.bin.gz \\
        --human-model b18c384nbt-humanv0.bin.gz --config analysis.cfg --out ratings.json

Count the ratings per game and rank with the current rules; several recordings are merged:

    python3 scripts/measure_coach_ratings.py summary ratings.json
"""

from __future__ import annotations

import argparse
import json
import math
import random
import subprocess
import sys
import time
from dataclasses import dataclass

COLUMNS = "ABCDEFGHJKLMNOPQRST"
RANKS = ("rank_10k", "rank_3k", "rank_1d", "rank_3d", "rank_5d", "rank_9d")
VERDICTS = ("brilliant", "good", "slow", "problem", "blunder")

# RankAwareWinrate
FALLBACK_SCORE_STDEV = 12.0
MIN_SCORE_STDEV = 1.0
# MoveVerdict
SLOW_LOSS = 0.05
PROBLEM_LOSS = 0.10
BLUNDER_LOSS = 0.20
DECIDED = 0.05
PROBLEM_POINTS = 10.0
BLUNDER_POINTS = 20.0
# PositionReview and MoveFeedback
MIN_RELIABLE_VISITS = 16
RARE_SHARE = 0.10
# MoveReviewer: the review while the player thinks, and of the position after a move it missed
TURN_VISITS, TURN_SECONDS = 400, 1.5
AFTER_VISITS, AFTER_SECONDS = 200, 0.8
# Stand-ins only pass once the game is nearly over.
FIRST_PASS = 150


def win_chance(lead: float, stdev: float) -> float:
    """RankAwareWinrate.of: the chance to win with `lead` against the rank's score spread."""
    if stdev is not None and math.isfinite(stdev) and stdev > 0:
        spread = max(MIN_SCORE_STDEV, stdev)
    else:
        spread = FALLBACK_SCORE_STDEV
    return 0.5 * (1.0 + math.erf(lead / spread / math.sqrt(2.0)))


def verdict(win_before: float, win_after: float, points_lost: float) -> str:
    """MoveVerdict.of, without 妙手."""
    loss = math.floor(max(0.0, win_before - win_after) * 100.0 + 0.5) / 100.0
    rating = 4 if loss >= BLUNDER_LOSS else 3 if loss >= PROBLEM_LOSS else 2 if loss >= SLOW_LOSS else 1
    if win_before < DECIDED or win_before > 1.0 - DECIDED:
        by_points = 4 if points_lost >= BLUNDER_POINTS else 3 if points_lost >= PROBLEM_POINTS else 1
        rating = max(rating, by_points)
    return VERDICTS[rating]


def move_name(index: int, size: int = 19) -> str:
    if index == size * size:
        return "pass"
    return f"{COLUMNS[index % size]}{size - index // size}"


@dataclass
class Review:
    """PositionReview: KataGo's search of a position, for the side to move."""

    root_lead: float
    stdev: float
    candidates: list  # (move, visits, score lead), KataGo's choice first
    shares: dict  # move -> share of players of the rank who play it

    @staticmethod
    def parse(response: dict, size: int = 19) -> Review:
        root = response.get("rootInfo", {})
        infos = sorted(response.get("moveInfos", []), key=lambda info: info.get("order", 0))
        candidates = [(info["move"], info.get("visits", 0), info["scoreLead"])
                      for info in infos if "scoreLead" in info]
        shares = {move_name(i, size): share for i, share in enumerate(human_policy(response)) if share > 0}
        return Review(root.get("scoreLead", math.nan), root.get("humanScoreStdev", math.nan),
                      candidates, shares)

    def best(self):
        return self.candidates[0] if self.candidates else None

    def lead_after(self, move: str) -> float:
        """The lead after `move`, or NaN when the search did not look at it closely enough."""
        best = self.best()
        if best is None:
            return math.nan
        if best[0] == move:
            return best[2]
        needed = max(MIN_RELIABLE_VISITS, best[1] // 20)
        for name, visits, lead in self.candidates:
            if name == move:
                return lead if visits >= needed else math.nan
        return math.nan

    def share(self, move: str) -> float:
        return self.shares.get(move, 0.0 if self.shares else math.nan)

    def common_move(self):
        return min(self.shares, key=lambda move: (-self.shares[move], move)) if self.shares else None


def human_policy(response: dict) -> list:
    return response.get("humanPolicy") or response.get("rootInfo", {}).get("humanPolicy") or []


def judge(before: Review, move: str, after: Review | None = None) -> dict | None:
    """MoveFeedback.judge: `move` played where `before` was reviewed, with the rating's inputs."""
    best = before.best()
    if best is None:
        return None
    best_lead = best[2]
    lead = before.lead_after(move)
    if math.isnan(lead) and after is not None:
        lead = -after.root_lead
    if not (math.isfinite(lead) and math.isfinite(best_lead)):
        return None
    stdev = before.stdev if math.isfinite(before.stdev) or after is None else after.stdev
    common = before.common_move()
    common_lead = before.lead_after(common) if common is not None and common != move else math.nan
    record = {
        "before": win_chance(best_lead, stdev),
        "after": win_chance(lead, stdev),
        "lost": max(0.0, best_lead - lead),
        "stdev": stdev,
        "best": best[0] == move,
        "share": before.share(move),
        # How much the move most players of the rank play here loses, when the search knows.
        "commonLoss": (win_chance(best_lead, stdev) - win_chance(common_lead, before.stdev)
                       if math.isfinite(common_lead) else None),
        "inTree": not math.isnan(before.lead_after(move)),
    }
    record["rating"] = rate(record)
    return record


def rate(record: dict) -> str:
    """The rating of a recorded move under the current rules."""
    rating = verdict(record["before"], record["after"], record["lost"])
    common_loss = record.get("commonLoss")
    if (rating == "good" and record["best"] and record["share"] < RARE_SHARE
            and common_loss is not None and common_loss >= SLOW_LOSS):
        return "brilliant"
    return rating


def sample_move(policy: list, rng: random.Random, allow_pass: bool, size: int = 19) -> str:
    """A move drawn from the HumanSL policy; before the end a drawn pass becomes the usual move."""
    legal = [(index, share) for index, share in enumerate(policy) if share > 0]
    pick = rng.random() * sum(share for _, share in legal)
    choice = legal[-1][0]
    total = 0.0
    for index, share in legal:
        total += share
        if pick < total:
            choice = index
            break
    if choice == size * size and not allow_pass:
        choice = max((index for index, _ in legal if index != size * size), key=lambda i: policy[i])
    return move_name(choice, size)


def is_elite(rank: str) -> bool:
    return rank.startswith("proyear_") or rank in ("rank_7d", "rank_8d", "rank_9d")


class Engine:
    def __init__(self, command: list):
        self.process = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                        stderr=subprocess.DEVNULL, text=True, bufsize=1)
        self.count = 0

    def analyze(self, moves: list, rank: str, visits: int, seconds: float | None = None) -> dict:
        self.count += 1
        query = {
            "id": f"q{self.count}", "moves": moves, "rules": "chinese", "komi": 7.5,
            "boardXSize": 19, "boardYSize": 19, "maxVisits": visits, "includePolicy": True,
            "overrideSettings": {
                "humanSLProfile": rank, "ignorePreRootHistory": False,
                "humanSLRootExploreProbWeightless": 0.8 if is_elite(rank) else 0.5,
                "rootNumSymmetriesToSample": 1, "reportAnalysisWinratesAs": "SIDETOMOVE",
            },
        }
        if seconds:
            query["overrideSettings"]["maxTime"] = seconds
        self.process.stdin.write(json.dumps(query) + "\n")
        self.process.stdin.flush()
        while True:
            line = self.process.stdout.readline()
            if not line:
                raise RuntimeError("KataGo exited")
            response = json.loads(line)
            if response.get("id") != query["id"] or "warning" in response:
                continue
            if "error" in response:
                raise RuntimeError(response["error"])
            if not response.get("isDuringSearch"):
                return response

    def close(self):
        self.process.stdin.close()
        self.process.wait(timeout=30)


def play_game(engine: Engine, rank: str, rng: random.Random, args) -> tuple:
    moves, records = [], []
    for turn in range(args.moves):
        color = "BW"[turn % 2]
        if color == "B":
            response = engine.analyze(moves, rank, args.turn_visits, args.turn_seconds)
        else:
            response = engine.analyze(moves, rank, 1)
        move = sample_move(human_policy(response), rng, turn >= FIRST_PASS)
        if color == "B":
            before = Review.parse(response)
            after = None
            if math.isnan(before.lead_after(move)):
                after = Review.parse(engine.analyze(moves + [[color, move]], rank,
                                                    args.after_visits, args.after_seconds))
            record = judge(before, move, after)
            if record is not None:
                record.update(rank=rank, move=turn + 1, played=move,
                              visits=response.get("rootInfo", {}).get("visits"))
                records.append(record)
        moves.append([color, move])
        if len(moves) >= 2 and moves[-1][1] == moves[-2][1] == "pass":
            break
    return moves, records


def play(args) -> None:
    overrides = "numAnalysisThreads=1,numSearchThreadsPerAnalysisThread=8,nnCacheSizePowerOfTwo=20"
    if args.override_config:
        overrides += "," + args.override_config
    engine = Engine([args.katago, "analysis", "-model", args.model, "-human-model", args.human_model,
                     "-config", args.config, "-override-config", overrides])
    rng = random.Random(args.seed)
    games, records = [], []
    try:
        for rank in args.ranks.split(","):
            for game in range(args.games):
                started = time.time()
                moves, judged = play_game(engine, rank, rng, args)
                for record in judged:
                    record["game"] = game
                games.append({"rank": rank, "game": game, "moves": moves})
                records.extend(judged)
                counts = {name: sum(1 for r in judged if r["rating"] == name) for name in VERDICTS}
                print(f"{rank} game {game + 1}: {len(moves)} moves, {len(judged)} judged, "
                      + " ".join(f"{name} {count}" for name, count in counts.items())
                      + f" ({time.time() - started:.0f} s)", flush=True)
                with open(args.out, "w", encoding="utf-8") as out:
                    json.dump({"settings": {k: v for k, v in vars(args).items() if k != "func"},
                               "games": games, "moves": records}, out)
    finally:
        engine.close()


def summarize(recordings: list) -> dict:
    """Per rank: games, judged moves, and each rating's count per game, overall and once decided."""
    table = {}
    for number, recording in enumerate(recordings):
        for record in recording["moves"]:
            row = table.setdefault(record["rank"], {"games": {}, "moves": 0})
            game = row["games"].setdefault((number, record["game"]), {n: 0 for n in VERDICTS})
            decided = not (DECIDED <= record["before"] <= 1.0 - DECIDED)
            rating = rate(record)
            game[rating] += 1
            if decided:
                game.setdefault("decided " + rating, 0)
                game["decided " + rating] += 1
            row["moves"] += 1
    return table


def print_summary(table: dict) -> None:
    ranks = sorted(table, key=lambda r: RANKS.index(r) if r in RANKS else len(RANKS))
    print("| rank | games | judged per game | " + " | ".join(VERDICTS[2:]) + " | brilliant |")
    print("|---|---|---|---|---|---|---|")
    for rank in ranks:
        games = list(table[rank]["games"].values())
        cells = []
        for name in VERDICTS[2:] + VERDICTS[:1]:
            per_game = [game[name] for game in games]
            decided = sum(game.get("decided " + name, 0) for game in games) / len(games)
            cells.append(f"{sum(per_game) / len(games):.1f} ({min(per_game)}–{max(per_game)}; "
                         f"decided {decided:.1f})")
        print(f"| {rank} | {len(games)} | {table[rank]['moves'] / len(games):.0f} | "
              + " | ".join(cells) + " |")


def main(argv=None) -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(required=True)
    p = commands.add_parser("play", help="play stand-in games and record black's judged moves")
    p.add_argument("--katago", required=True)
    p.add_argument("--model", required=True)
    p.add_argument("--human-model", required=True)
    p.add_argument("--config", required=True, help="KataGo analysis config")
    p.add_argument("--override-config", default="",
                   help="extra overrides, e.g. nnMaxBatchSize=4,metalPadBatch=true")
    p.add_argument("--ranks", default=",".join(RANKS))
    p.add_argument("--games", type=int, default=3)
    p.add_argument("--moves", type=int, default=220)
    p.add_argument("--turn-visits", type=int, default=TURN_VISITS)
    p.add_argument("--turn-seconds", type=float, default=TURN_SECONDS)
    p.add_argument("--after-visits", type=int, default=AFTER_VISITS)
    p.add_argument("--after-seconds", type=float, default=AFTER_SECONDS)
    p.add_argument("--seed", type=int, default=7)
    p.add_argument("--out", required=True)
    p.set_defaults(func=play)
    s = commands.add_parser("summary", help="count the ratings in recordings")
    s.add_argument("recordings", nargs="+")
    s.set_defaults(func=lambda a: print_summary(summarize(
        [json.load(open(path, encoding="utf-8")) for path in a.recordings])))
    args = parser.parse_args(argv)
    args.func(args)


if __name__ == "__main__":
    sys.exit(main())
