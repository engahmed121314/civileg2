import hashlib
import io
from pathlib import Path
import tempfile
import unittest
import urllib.error
from unittest import mock

import prepare_robolectric_sdk as sdk


class PrepareRobolectricSdkTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.directory = Path(self.temp.name)
        self.addCleanup(self.temp.cleanup)
        self.payload = b"synthetic-sdk-bytes"
        self.digest = hashlib.sha512(self.payload).hexdigest()
        patch = mock.patch.object(sdk, "SHA512", self.digest)
        patch.start()
        self.addCleanup(patch.stop)

    def test_valid_cache_is_verified_without_any_network_request(self):
        target = self.directory / sdk.ARTIFACT
        target.write_bytes(self.payload)
        opener = mock.Mock(side_effect=AssertionError("Unexpected network request"))
        self.assertEqual(target, sdk.prepare_sdk(self.directory, opener))
        opener.assert_not_called()

    def test_invalid_cache_fails_without_network_or_overwriting_the_file(self):
        target = self.directory / sdk.ARTIFACT
        target.write_bytes(b"invalid-cache")
        opener = mock.Mock()
        with self.assertRaisesRegex(RuntimeError, "Cached.*checksum mismatch"):
            sdk.prepare_sdk(self.directory, opener)
        self.assertEqual(b"invalid-cache", target.read_bytes())
        opener.assert_not_called()

    def test_download_is_checked_then_atomically_installed(self):
        opener = mock.Mock(return_value=io.BytesIO(self.payload))
        target = sdk.prepare_sdk(self.directory, opener)
        self.assertEqual(self.payload, target.read_bytes())
        opener.assert_called_once_with(sdk.DOWNLOAD_URL, timeout=120)
        self.assertEqual([sdk.ARTIFACT], [p.name for p in self.directory.iterdir()])

    def test_bad_download_never_becomes_a_cached_sdk(self):
        opener = mock.Mock(return_value=io.BytesIO(b"wrong-sdk"))
        with self.assertRaisesRegex(RuntimeError, "Downloaded.*checksum mismatch"):
            sdk.prepare_sdk(self.directory, opener)
        opener.assert_called_once()
        self.assertEqual([], list(self.directory.iterdir()))

    def test_http429_stops_after_one_attempt_and_reports_retry_after(self):
        error = urllib.error.HTTPError(sdk.DOWNLOAD_URL, 429, "rate limit", {"Retry-After": "120"}, None)
        opener = mock.Mock(side_effect=error)
        with self.assertRaisesRegex(RuntimeError, "HTTP429.*without retry.*120"):
            sdk.prepare_sdk(self.directory, opener)
        opener.assert_called_once()
        self.assertEqual([], list(self.directory.iterdir()))

    def test_interrupted_download_removes_partial_and_does_not_retry(self):
        response = mock.MagicMock()
        response.__enter__.return_value = response
        response.read.side_effect = [b"partial", OSError("connection lost")]
        opener = mock.Mock(return_value=response)
        with self.assertRaisesRegex(OSError, "connection lost"):
            sdk.prepare_sdk(self.directory, opener)
        opener.assert_called_once()
        self.assertEqual([], list(self.directory.iterdir()))


if __name__ == "__main__":
    unittest.main()
