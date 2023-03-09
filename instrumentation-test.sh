#!/bin/bash

set +e # don't abort if commands exit with non zero exit code
gcloud firebase test android run \
    --app $APPLICATION_APK \
    --test $INSTRUMENTATION_APK \
    --results-dir $GCLOUD_BUCKET_DIRECTORY
code=$?

fetched_results="false"
# https://firebase.google.com/docs/test-lab/android/command-line#script_exit_codes
# 0 = success
# 10 = test failure
if [[ "$code" == "0" || "$code" == "10" ]]; then
    gsutil cp -r \
        gs://$GCLOUD_BUCKET/$GCLOUD_BUCKET_DIRECTORY \
        $LOCAL_DIRECTORY
    fetched_results="true"
fi

echo "fetched_results=$fetched_results" >> $GITHUB_OUTPUT

exit $code
