#!/usr/bin/env python3

import unittest

import check_line_endings


class CheckLineEndingsTest(unittest.TestCase):
    def test_lf_accepts_lf_content(self):
        self.assertEqual([], check_line_endings.content_errors("sample.txt", b"a\nb\n", "lf"))

    def test_lf_rejects_crlf_and_bare_cr(self):
        errors = check_line_endings.content_errors("sample.txt", b"a\r\nb\rc\n", "lf")
        self.assertEqual(1, len(errors))
        self.assertIn("1 CRLF", errors[0])
        self.assertIn("1 bare CR", errors[0])

    def test_crlf_accepts_crlf_content(self):
        self.assertEqual(
            [], check_line_endings.content_errors("sample.cmd", b"a\r\nb\r\n", "crlf")
        )

    def test_crlf_rejects_lf_and_bare_cr(self):
        errors = check_line_endings.content_errors("sample.cmd", b"a\r\nb\nc\r", "crlf")
        self.assertEqual(1, len(errors))
        self.assertIn("1 bare LF", errors[0])
        self.assertIn("1 bare CR", errors[0])

    def test_binary_content_requires_binary_attribute(self):
        errors = check_line_endings.content_errors("sample.dat", b"a\0b", "lf")
        self.assertEqual(
            ["sample.dat: looks binary but is not marked binary in .gitattributes"], errors
        )

    def test_content_without_newline_is_valid(self):
        self.assertEqual([], check_line_endings.content_errors("sample.txt", b"value", "lf"))
        self.assertEqual([], check_line_endings.content_errors("sample.cmd", b"value", "crlf"))


if __name__ == "__main__":
    unittest.main()
