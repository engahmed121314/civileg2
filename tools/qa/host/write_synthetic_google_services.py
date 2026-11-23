#!/usr/bin/env python3
"""Write a clearly synthetic, non-sensitive google-services.json for CI QA builds.

The real file is not in the QA branch. These values are placeholders with no
associated Firebase project; Crashlytics mapping upload is disabled by the build
invocation. Never point this at a real project.
"""
import json
import sys

doc = {
    "project_info": {
        "project_number": "000000000000",
        "project_id": "synthetic-qa-not-a-real-project",
        "storage_bucket": "synthetic-qa-not-a-real-project.appspot.com",
    },
    "client": [
        {
            "client_info": {
                "mobilesdk_app_id": "1:000000000000:android:0000000000000000000000",
                "android_client_info": {"package_name": "com.civileg.app"},
            },
            "api_key": [{"current_key": "SYNTHETIC_QA_KEY_NOT_A_CREDENTIAL"}],
            "oauth_client": [],
            "services": {"appinvite_service": {"other_platform_oauth_client": []}},
        }
    ],
    "configuration_version": "1",
}

out = sys.argv[1] if len(sys.argv) > 1 else "app/google-services.json"
with open(out, "w") as f:
    json.dump(doc, f, indent=2)
print(f"wrote synthetic google-services.json to {out}")
