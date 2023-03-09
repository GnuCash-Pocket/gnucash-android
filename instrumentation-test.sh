#!/bin/bash

set +e # don't abort if commands exit with non zero exit code
gcloud firebase test android run \
    --app $APPLICATION_APK \
    --test $INSTRUMENTATION_APK \
    --results-dir $GCLOUD_BUCKET_DIRECTORY
code=$?

fetched_results="false"
# https://firebase.google.com/docs/test-lab/android/command-line#script_exit_codes
if [[ "$code" == "0" || "$code" == "10" ]]; then
    gcloud_url="gs://$GCLOUD_BUCKET/$GCLOUD_BUCKET_DIRECTORY"
    echo "fetching results from $gcloud_url"

    gsutil cp -r $gcloud_url $LOCAL_DIRECTORY
    fetched_results="true"

    echo "successfully saved results to $LOCAL_DIRECTORY"
    ls $LOCAL_DIRECTORY
fi

echo "fetched_results=$fetched_results" >> $GITHUB_OUTPUT

exit $code
