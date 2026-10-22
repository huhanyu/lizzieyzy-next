import math
import unittest

import measure_coach_ratings as ratings

TURN = ratings.Review(0.4, 12.0, [("Q16", 300, 0.5), ("D4", 80, -3.5)], {"D4": 0.55, "Q16": 0.2})


class FixedRandom:
    def __init__(self, value):
        self.value = value

    def random(self):
        return self.value


class MeasureCoachRatingsTest(unittest.TestCase):
    def test_verdict_rates_like_the_app(self):
        # The cases of MoveVerdictTest.
        cases = [
            ((0.50, 0.46, 1.0), "good"),
            ((0.50, 0.44, 1.0), "slow"),
            ((0.50, 0.39, 3.0), "problem"),
            ((0.50, 0.29, 6.0), "blunder"),
            ((0.50, 0.451, 1.0), "slow"),
            ((0.50, 0.4551, 1.0), "good"),
            ((0.50, 0.48, 9.0), "good"),
            ((0.02, 0.01, 10.0), "problem"),
            ((0.99, 0.97, 16.0), "problem"),
            ((0.99, 0.97, 20.0), "blunder"),
            ((0.02, 0.02, 2.0), "good"),
            ((0.99, 0.98, 5.0), "good"),
            ((0.99, 0.98, 9.0), "good"),
            ((0.97, 0.70, 2.0), "blunder"),
        ]
        for arguments, expected in cases:
            self.assertEqual(expected, ratings.verdict(*arguments), arguments)

    def test_win_chance_falls_back_without_a_usable_spread(self):
        self.assertAlmostEqual(0.8413447, ratings.win_chance(12.0, math.nan), places=6)
        self.assertEqual(ratings.win_chance(0.5, 1.0), ratings.win_chance(0.5, 0.1))

    def test_a_searched_move_is_judged_from_the_turn_and_another_from_the_position_after(self):
        searched = ratings.judge(TURN, "D4")
        self.assertEqual("problem", searched["rating"])
        self.assertTrue(searched["inTree"])

        self.assertIsNone(ratings.judge(TURN, "A1"))
        after = ratings.Review(14.5, 11.0, [], {})
        missed = ratings.judge(TURN, "A1", after)
        self.assertEqual("blunder", missed["rating"])
        self.assertFalse(missed["inTree"])
        self.assertAlmostEqual(15.0, missed["lost"])

    def test_a_rare_best_move_is_brilliant_only_where_the_usual_move_loses(self):
        trap = ratings.Review(0.0, 12.0, [("Q16", 300, 0.5), ("D4", 100, -3.5)],
                              {"D4": 0.7, "Q16": 0.05})
        self.assertEqual("brilliant", ratings.judge(trap, "Q16")["rating"])

        harmless = ratings.Review(0.0, 12.0, [("Q16", 300, 0.5), ("D4", 100, 0.0)],
                                  {"D4": 0.7, "Q16": 0.05})
        self.assertEqual("good", ratings.judge(harmless, "Q16")["rating"])

    def test_parse_reads_the_search_and_the_human_policy(self):
        response = {
            "rootInfo": {"scoreLead": 1.5, "humanScoreStdev": 9.0},
            "moveInfos": [
                {"move": "B1", "order": 1, "visits": 30, "scoreLead": 0.5},
                {"move": "A2", "order": 0, "visits": 300, "scoreLead": 1.5},
            ],
            "humanPolicy": [0.1, 0.6, -1, 0.2, 0.1],
        }
        review = ratings.Review.parse(response, size=2)
        self.assertEqual(("A2", 300, 1.5), review.best())
        self.assertEqual(9.0, review.stdev)
        self.assertEqual("B2", review.common_move())
        self.assertEqual(0.0, review.share("A1"), "an illegal point")
        self.assertEqual(0.5, review.lead_after("B1"))
        self.assertTrue(math.isnan(review.lead_after("A1")), "not searched")

    def test_stand_ins_follow_the_policy_and_pass_only_near_the_end(self):
        policy = [0.0, 0.7, -1, 0.0, 0.3]
        self.assertEqual("B2", ratings.sample_move(policy, FixedRandom(0.1), True, size=2))
        self.assertEqual("pass", ratings.sample_move(policy, FixedRandom(0.8), True, size=2))
        self.assertEqual("B2", ratings.sample_move(policy, FixedRandom(0.8), False, size=2))

    def test_summary_counts_each_rating_per_game_across_recordings(self):
        def move(game, before, after, lost):
            return {"rank": "rank_3d", "game": game, "before": before, "after": after,
                    "lost": lost, "best": False, "share": 0.3}

        first = {"moves": [move(0, 0.50, 0.25, 8.0), move(0, 0.99, 0.98, 6.0)]}
        second = {"moves": [move(0, 0.50, 0.38, 4.0)]}
        table = ratings.summarize([first, second])

        row = table["rank_3d"]
        self.assertEqual(3, row["moves"])
        games = list(row["games"].values())
        self.assertEqual(2, len(games), "games are told apart by recording")
        self.assertEqual((1, 1, 0), (games[0]["blunder"], games[0]["good"], games[0]["problem"]))
        self.assertEqual(1, games[0]["decided good"])
        self.assertEqual(1, games[1]["problem"])


if __name__ == "__main__":
    unittest.main()
