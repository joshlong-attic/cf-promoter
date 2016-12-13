#!/usr/bin/env bash

mvn clean install
an=cf-promoter

cf push -p target/bg*jar cf-promoter --no-start
cf set-env $an CF_ORG $CF_ORG
cf set-env $an CF_SPACE $CF_SPACE
cf set-env $an CF_USER $CF_USER
cf set-env $an CF_PASSWORD $CF_PASSWORD
cf set-env $an CF_API $CF_API

cf start $an

