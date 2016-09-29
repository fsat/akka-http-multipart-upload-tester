#!/bin/bash

set -ei
curl -v --form "bundleConf=@src/main/resources/bundle.conf" --form "bundle=@src/main/resources/dummy-bundle-3e1f22ec0b81843e6273c300f0ba3958e0cdc6f65da58e02e1c9dddd3d3cab29.zip" http://127.0.0.1:5555/test

