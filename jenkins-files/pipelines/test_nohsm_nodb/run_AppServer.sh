#!/bin/sh

${APPSRV_HOME}/bin/standalone.sh -b 0.0.0.0 -bmanagement 0.0.0.0 &

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

