#!/bin/sh

echo '=================== CHECKING JAVA VERSION: ================================='
java -version

# Set SIGNSERVER_HOME

cd signserver-ee*
export SIGNSERVER_HOME=.

# Set SIGNSERVER_NODEID
export SIGNSERVER_NODEID="magnum-ci"

echo '=================== Setup audit log ======================================='
AUDITLOG=${APPSRV_HOME}/standalone/log/signserver_audit.log
# Audit log file
echo "[SCRIPT] Removing old audit log"
rm -f signserver_audit.log
ln -s ${AUDITLOG} signserver_audit.log
rm -f ${AUDITLOG}

echo "Trusting DSSRootCA10"
keytool -import -keystore ${JAVA_HOME}/lib/security/cacerts -file res/test/dss10/DSSRootCA10.cacert.pem -alias DSSRootCA10 -noprompt -storepass changeit
keytool -exportcert -keystore ${JAVA_HOME}/lib/security/cacerts -alias DSSRootCA10 -file /dev/null -noprompt -storepass changeit
if [ $? -ne 0 ]; then echo "Build step 2 failed: trusting DSSRootCA10"; exit 1; fi

# Clear maintenance file
echo "Clearing maintenance file"
cat > ${SIGNSERVER_HOME}/maintenance.properties << EOF
DOWN_FOR_MAINTENANCE=false
EOF

# Make sure we can communicate with SignServer or otherwise fail fast
chmod +x bin/signserver
bin/signserver getstatus brief all
if [ $? -ne 0 ]; then echo "Running SignServer CLI failed"; exit 1; fi

bin/ant systemtest:jars -Dsystemtest.excludes="**/ArchivingCLITest*,**/GroupKeyServiceCLITest*,**/Base64DatabaseArchiverTest*,**/OldDatabaseArchiverTest*,,**/GroupKeyServiceTest*,**/ArchiveTest*,**/AuditLogCLITest*,**/VerifyLogCommandTest*,**/DatabaseCLITest*" -Dno.clover=true
