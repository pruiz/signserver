#!/bin/bash

# Find directory for this script
SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ] ; do SOURCE="$(readlink -f "$SOURCE")"; done
DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"

# Install DeployTools parent POM
DPL_VERSION=2.2.1-SNAPSHOT
mvn -Dmaven.repo.local=${MAVEN_REPO_LOCAL} install:install-file -DgroupId=org.signserver.deploytools -DartifactId=DeployTools -Dversion=${DPL_VERSION} -Dpackaging=pom -Dfile=${DIR}/DeployTools-${DPL_VERSION}-pom.xml $@

# Install the DeployTools artifacts from JARS
# Based on post by David Blevins: https://www.mail-archive.com/users@maven.apache.org/msg91297.html
for jar in ${DIR}/DeployTools-Common-${DPL_VERSION}.jar ${DIR}/DeployTools-Maven-${DPL_VERSION}.jar; do
    pom=$(jar tvf $jar | grep pom.xml | perl -pe 's,.* ,,')
    props=$(jar tvf $jar | grep pom.properties | perl -pe 's,.* ,,')

    if [ -n "$pom" ]; then
        jar xvf $jar $pom $props
        source $props

        mvn -Dmaven.repo.local=${MAVEN_REPO_LOCAL} install:install-file -DgroupId=$groupId -DartifactId=$artifactId -Dversion=$version -Dpackaging=jar -Dfile=$jar $@
        mvn -Dmaven.repo.local=${MAVEN_REPO_LOCAL} install:install-file -DgroupId=$groupId -DartifactId=$artifactId -Dversion=$version -Dpackaging=pom -Dfile=$pom $@

    else
        echo "missing POM.xml in $jar"
    fi
done
rm META-INF -r
