#!/bin/sh

echo '=================== CHECKING JAVA VERSION: ================================='
java -version

#cp /opt/standalone1.xml /opt/jboss/wildfly/standalone/configuration/standalone.xml

#cp /opt/conf/* /app/ejbca/conf/

cd signserver-ee*
export SIGNSERVER_HOME=.

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

${APPSRV_HOME}/bin/standalone.sh -b 0.0.0.0 -bmanagement 0.0.0.0 &

#ant clean deployear

echo '=================== Waiting for deploy ================================='


wait_for_deployment() {
    DEPLOY_SUCCESSFUL=0
	# Wait for up to 180 seconds for app to start up
	for i in {1..90} ; do
		if [ -e "${APPSRV_HOME}/standalone/deployments/signserver.ear.deployed" ] ; then
			echo "SignServer successfully started."
			DEPLOY_SUCCESSFUL=1
			break
		fi
		if [ -e "${APPSRV_HOME}/standalone/deployments/signserver.ear.failed" ] ; then
            echo "SignServer deploy failed."
            exit 1;
        fi
		echo 'waiting...'
		sleep 2
	done
    if [ "$DEPLOY_SUCCESSFUL" -ne 1 ]; then
        echo "SignServer deploy timed out." 
        exit 1;
    fi
}

wait_for_deployment
echo '=================== ant deployear done and successfully deployed! ================================='
#
#ant runinstall
#echo '=================== ant runinstall done! ================================='
#
#ant deploy-keystore
#echo '=================== ant deploy-keystore done! ================================='

# load the final version of Wildfly conf and restart wildfly
#cp /opt/standalone2.xml /opt/jboss/wildfly/standalone/configuration/standalone.xml
#/opt/jboss/wildfly/bin/jboss-cli.sh -c --command=:reload

# wait for reload to kick in and start undeploying and drop ejbca.ear.deployed file (otherwise we'd detect ejbca.ear.deployed file immediately again)
#sleep 10

#wait_for_deployment
#
#echo '=================== starting system tests ================================='
#ant test:runsys

chmod +x bin/signserver

ls ${APPSRV_HOME}/standalone/tmp/auth/ -la
id
touch ${APPSRV_HOME}/standalone/tmp/auth/test123

bin/signserver getstatus brief all
if [ $? -ne 0 ]; then echo "Running SignServer CLI failed"; exit 1; fi

bin/ant systemtest:jars -Dsystemtest.excludes="**/ArchivingCLITest*,**/GroupKeyServiceCLITest*,**/Base64DatabaseArchiverTest*,**/OldDatabaseArchiverTest*,,**/GroupKeyServiceTest*,**/ArchiveTest*,**/AuditLogCLITest*,**/VerifyLogCommandTest*,**/DatabaseCLITest*" -Dno.clover=true
