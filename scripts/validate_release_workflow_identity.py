#!/usr/bin/env python3
"""Fail-closed identity guard for workflows that mutate a GitHub release."""

from __future__ import annotations

import argparse
from datetime import date
import json
import os
import re
import sys
import time
from typing import Callable
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request, urlopen


API_VERSION = "2026-03-10"


class IdentityError(RuntimeError):
    """The requested workflow target is ambiguous or does not match the release."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise IdentityError(message)


def validate_requested_identity(
    date_tag: str,
    release_tag: str,
    prerelease: bool,
    repository: str,
    tag_sha: str,
    releases: list[object],
    *,
    require_draft: bool,
    target_sha: str | None = None,
    github_ref: str | None = None,
) -> dict[str, object]:
    try:
        parsed_date = date.fromisoformat(date_tag)
    except ValueError as exc:
        raise IdentityError(f"Invalid release date: {date_tag}") from exc
    require(parsed_date.isoformat() == date_tag, f"Invalid release date: {date_tag}")
    require(
        re.fullmatch(rf"next-{re.escape(date_tag)}\.[1-9][0-9]*", release_tag)
        is not None,
        "release_tag must exactly match date_tag and use a positive serial",
    )
    require(
        re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository) is not None,
        "repository must use owner/name format",
    )
    require(
        re.fullmatch(r"[0-9a-f]{40}", tag_sha) is not None,
        "Release tag must resolve to a full commit SHA",
    )

    matches = [
        item
        for item in releases
        if isinstance(item, dict) and item.get("tag_name") == release_tag
    ]
    require(len(matches) == 1, f"Expected exactly one release for {release_tag}")
    release = matches[0]
    require(
        release.get("target_commitish") == tag_sha,
        "Release target_commitish must exactly match the release tag commit",
    )
    require(
        type(release.get("prerelease")) is bool
        and release.get("prerelease") is prerelease,
        f"Release prerelease must be {str(prerelease).lower()}",
    )
    if require_draft:
        require(release.get("draft") is True, "Release uploads require an existing draft")

    if target_sha is not None or github_ref is not None:
        require(target_sha is not None and github_ref is not None, "Target SHA and ref are paired")
        assert target_sha is not None and github_ref is not None
        require(
            re.fullmatch(r"[0-9a-f]{40}", target_sha) is not None,
            "target_sha must be a full commit SHA",
        )
        require(target_sha == tag_sha, "Workflow target SHA must match release tag commit")
        require(
            github_ref == f"refs/tags/{release_tag}",
            "Release build must be dispatched from the exact release tag ref",
        )
    return release


class GitHubIdentityClient:
    def __init__(
        self,
        repository: str,
        token: str,
        api_url: str,
        *,
        sleep: Callable[[float], None] = time.sleep,
        retry_attempts: int = 5,
    ) -> None:
        require(bool(token), "GITHUB_TOKEN is required")
        require(retry_attempts > 0, "retry_attempts must be positive")
        self.repository = repository
        self.token = token
        self.api_url = api_url.rstrip("/")
        self.sleep = sleep
        self.retry_attempts = retry_attempts

    @staticmethod
    def _transient(exc: HTTPError) -> bool:
        headers = exc.headers or {}
        return (
            exc.code == 429
            or 500 <= exc.code <= 599
            or (
                exc.code == 403
                and (
                    headers.get("Retry-After") is not None
                    or headers.get("X-RateLimit-Remaining") == "0"
                )
            )
        )

    @staticmethod
    def _retry_delay(exc: HTTPError | None, attempt: int) -> float:
        fallback = float(min(2**attempt, 30))
        if exc is None:
            return fallback
        headers = exc.headers or {}
        retry_after = headers.get("Retry-After")
        if retry_after:
            try:
                return float(max(0, min(int(retry_after), 60)))
            except ValueError:
                pass
        reset = headers.get("X-RateLimit-Reset")
        if reset:
            try:
                return float(max(0, min(int(reset) - int(time.time()) + 1, 60)))
            except ValueError:
                pass
        return fallback

    def get_json(self, path: str) -> object:
        for attempt in range(1, self.retry_attempts + 1):
            request = Request(
                f"{self.api_url}{path}",
                headers={
                    "Accept": "application/vnd.github+json",
                    "Authorization": f"Bearer {self.token}",
                    "User-Agent": "lizzieyzy-next-release-identity-guard",
                    "X-GitHub-Api-Version": API_VERSION,
                },
            )
            try:
                with urlopen(request, timeout=60) as response:
                    raw = response.read(16 * 1024 * 1024 + 1)
            except HTTPError as exc:
                detail = exc.read(2000).decode("utf-8", errors="replace")
                if self._transient(exc) and attempt < self.retry_attempts:
                    self.sleep(self._retry_delay(exc, attempt))
                    continue
                raise IdentityError(
                    f"GitHub API request failed ({exc.code}): {detail}"
                ) from exc
            except OSError as exc:
                if attempt < self.retry_attempts:
                    self.sleep(self._retry_delay(None, attempt))
                    continue
                raise IdentityError(f"GitHub API request failed: {exc}") from exc
            break
        else:
            raise AssertionError("unreachable GitHub retry loop")
        require(len(raw) <= 16 * 1024 * 1024, "GitHub API response is too large")
        try:
            return json.loads(raw.decode("utf-8"))
        except json.JSONDecodeError as exc:
            raise IdentityError("GitHub API returned invalid JSON") from exc

    def tag_sha(self, release_tag: str) -> str:
        payload = self.get_json(
            f"/repos/{self.repository}/git/ref/tags/{quote(release_tag, safe='')}"
        )
        require(isinstance(payload, dict), "Git tag response must be an object")
        assert isinstance(payload, dict)
        obj = payload.get("object")
        require(
            isinstance(obj, dict) and obj.get("type") == "commit" and obj.get("sha"),
            "Release tag must be a lightweight commit tag",
        )
        assert isinstance(obj, dict)
        return str(obj["sha"])

    def releases(self) -> list[object]:
        payload = self.get_json(f"/repos/{self.repository}/releases?per_page=100")
        require(isinstance(payload, list), "GitHub releases response must be a list")
        assert isinstance(payload, list)
        return payload


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--date-tag", required=True)
    parser.add_argument("--release-tag", required=True)
    parser.add_argument("--prerelease", required=True, choices=("true", "false"))
    parser.add_argument("--repository", required=True)
    parser.add_argument("--target-sha")
    parser.add_argument("--github-ref")
    parser.add_argument("--require-draft", action="store_true")
    parser.add_argument("--api-url", default=os.environ.get("GITHUB_API_URL", "https://api.github.com"))
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        client = GitHubIdentityClient(
            args.repository,
            os.environ.get("GITHUB_TOKEN", ""),
            args.api_url,
        )
        tag_sha = client.tag_sha(args.release_tag)
        validate_requested_identity(
            args.date_tag,
            args.release_tag,
            args.prerelease == "true",
            args.repository,
            tag_sha,
            client.releases(),
            require_draft=args.require_draft,
            target_sha=args.target_sha,
            github_ref=args.github_ref,
        )
    except IdentityError as exc:
        print(f"Release workflow identity validation failed: {exc}", file=sys.stderr)
        return 1
    print(
        f"Validated release workflow identity: {args.release_tag} at {tag_sha}",
        flush=True,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
