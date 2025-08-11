#!/usr/bin/env bash

mvn -B -pl '!polardbx-cdc-test,!polardbx-cdc-assemble' install -DskipTests=true -Dautoconfig.skip
mvn test -B -pl '!polardbx-cdc-test,!polardbx-cdc-assemble,!polardbx-cdc-protocol' code-coverage:jacoco -Djacoco.skip=true -Dmaven.test.failure.ignore=true
