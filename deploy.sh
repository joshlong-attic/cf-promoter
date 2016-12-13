#!/usr/bin/env bash

mvn clean install

cf push -p target/bg*jar
