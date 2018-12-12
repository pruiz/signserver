# Use latest jboss/base-jdk:8 image as the base
FROM jboss/base-jdk:8

# Set the WILDFLY_VERSION env variable
ENV JBOSS_EAP_VERSION 7.1.0
ENV JBOSS_EAP_SHA256 682d2e7168c9f09cc019dce8f5a70e61169e2dc438dc44ba7352aba4e0634e20
ENV JBOSS_HOME /opt/jboss/jboss-eap-$JBOSS_EAP_VERSION

USER root

# Assume /binaries is mounted from the pipeline
COPY /binaries/jboss-eap-$JBOSS_EAP_VERSION.zip $HOME

# Add the JBoss EAP distribution to /opt, and make jboss the owner of the extracted tar content
# Make sure the distribution is available from a well-known place
RUN cd $HOME \
    && sha256sum jboss-eap-$JBOSS_EAP_VERSION.zip | grep $JBOSS_EAP_SHA256 \
    && unzip jboss-eap-$JBOSS_EAP_VERSION.tar.gz \
    && mv $HOME/jboss-eap-$JBOSS_EAP_VERSION $JBOSS_HOME \
    && rm jboss-$JBOSS_EAP_VERSION.tar.gz \
    && chown -R jboss:0 ${JBOSS_HOME} \
    && chmod -R g+rw ${JBOSS_HOME}

# Ensure signals are forwarded to the JVM process correctly for graceful shutdown
ENV LAUNCH_JBOSS_IN_BACKGROUND true

USER jboss

# Expose the ports we're interested in
EXPOSE 8080

# Set the default command to run on boot
# This will boot WildFly in the standalone mode and bind to all interface
CMD ["$JBOSS_EAP_HOME/bin/standalone.sh", "-b", "0.0.0.0"]
