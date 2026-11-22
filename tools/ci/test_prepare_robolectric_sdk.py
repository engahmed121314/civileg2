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


    def test_missing_resource_sdk_is_fetched_without_redownloading_cached_runtime(self):
        runtime = self.directory / sdk.ARTIFACT
        runtime.write_bytes(self.payload)
        resource_payload = b"synthetic-app-resources-sdk"
        resource_digest = hashlib.sha512(resource_payload).hexdigest()
        opener = mock.Mock(return_value=io.BytesIO(resource_payload))
        sdk.prepare_sdk(self.directory, opener)
        resource = sdk.prepare_sdk(
            self.directory, opener, artifact=sdk.APP_RESOURCES_ARTIFACT,
            expected_sha512=resource_digest, download_url=sdk.APP_RESOURCES_DOWNLOAD_URL,
        )
        self.assertEqual(self.payload, runtime.read_bytes())
        self.assertEqual(resource_payload, resource.read_bytes())
        opener.assert_called_once_with(sdk.APP_RESOURCES_DOWNLOAD_URL, timeout=120)

    def test_resource_download_uses_its_own_checksum_not_the_runtime_checksum(self):
        opener = mock.Mock(return_value=io.BytesIO(self.payload))
        with self.assertRaisesRegex(RuntimeError, "Downloaded.*checksum mismatch"):
            sdk.prepare_sdk(
                self.directory, opener, artifact=sdk.APP_RESOURCES_ARTIFACT,
                expected_sha512=hashlib.sha512(b"different-resource-bytes").hexdigest(),
                download_url=sdk.APP_RESOURCES_DOWNLOAD_URL,
            )
        opener.assert_called_once()
        self.assertEqual([], list(self.directory.iterdir()))

    def test_invalid_resource_cache_is_not_overwritten_or_downloaded_again(self):
        resource = self.directory / sdk.APP_RESOURCES_ARTIFACT
        resource.write_bytes(b"invalid-resource-cache")
        opener = mock.Mock()
        with self.assertRaisesRegex(RuntimeError, "Cached.*checksum mismatch"):
            sdk.prepare_sdk(
                self.directory, opener, artifact=sdk.APP_RESOURCES_ARTIFACT,
                expected_sha512=self.digest, download_url=sdk.APP_RESOURCES_DOWNLOAD_URL,
            )
        opener.assert_not_called()
        self.assertEqual(b"invalid-resource-cache", resource.read_bytes())

    def test_cli_prepares_both_required_sdks_with_their_independent_pins(self):
        with mock.patch.object(sdk.sys, "argv", ["prepare", "--directory", str(self.directory)]):
            with mock.patch.object(sdk, "prepare_sdk") as prepare:
                self.assertEqual(0, sdk.main())
        self.assertEqual(
            [mock.call(str(self.directory), artifact=name, expected_sha512=checksum,
                       download_url=url) for name, checksum, url in sdk.SDK_ARTIFACTS],
            prepare.call_args_list,
        )
        self.assertEqual(2, prepare.call_count)

    def test_resource_http429_stops_cli_and_does_not_retry_or_fallback(self):
        with mock.patch.object(sdk.sys, "argv", ["prepare", "--directory", str(self.directory)]):
            with mock.patch.object(sdk, "prepare_sdk", side_effect=[self.directory / sdk.ARTIFACT,
                                   RuntimeError("Robolectric SDK HTTP429; stopped without retry")]) as prepare:
                with mock.patch.object(sdk.sys, "stderr", new_callable=io.StringIO) as errors:
                    self.assertEqual(1, sdk.main())
        self.assertEqual(2, prepare.call_count)
        self.assertIn("HTTP429", errors.getvalue())
        self.assertIn("without retry", errors.getvalue())

    def test_pins_cover_the_runtime_and_app_resource_artifacts_without_sdk_changes(self):
        self.assertEqual([sdk.ARTIFACT, sdk.APP_RESOURCES_ARTIFACT],
                         [name for name, _, _ in sdk.SDK_ARTIFACTS])
        for name, digest, url in sdk.SDK_ARTIFACTS:
            self.assertRegex(digest, r"^[0-9a-f]{128}$")
            self.assertTrue(url.endswith("/" + name))
        self.assertIn("14-robolectric-10818077-i7", sdk.ARTIFACT)
        self.assertIn("15-robolectric-12650502-i7", sdk.APP_RESOURCES_ARTIFACT)


if __name__ == "__main__":
    unittest.main()
