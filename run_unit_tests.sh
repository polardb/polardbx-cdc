#!/usr/bin/env bash

mvn -B -Ptest -pl '!polardbx-cdc-test,!polardbx-cdc-assemble' install -DskipTests=true -Dautoconfig.skip
mvn test -B -Ptest -pl '!polardbx-cdc-test,!polardbx-cdc-assemble,!polardbx-cdc-protocol' -Dmaven.test.failure.ignore=true
