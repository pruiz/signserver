# Use latest jboss/base-jdk:8 image as the base
FROM jboss/base-jdk:8

USER root

# Install Ant
RUN yum update -y \
    && yum install -y ant \
    && yum clean all

# Set the WILDFLY_VERSION env variable
ENV JBOSS_EAP_VERSION 7.1.0
ENV JBOSS_EAP_FOLDER jboss-eap-7.1
ENV JBOSS_EAP_SHA256 682d2e7168c9f09cc019dce8f5a70e61169e2dc438dc44ba7352aba4e0634e20
ENV JBOSS_HOME /opt/jboss/$JBOSS_EAP_FOLDER
ENV APPSRV_HOME $JBOSS_HOME

WORKDIR /root

COPY binaries/jboss-eap-$JBOSS_EAP_VERSION.zip ./

# Assume /binaries is mounted from the pipeline
# Add the JBoss EAP distribution to /opt, and make jboss the owner of the extracted tar content
# Make sure the distribution is available from a well-known place
RUN cd $HOME \
    && ls \
    && pwd \
    && sha256sum jboss-eap-$JBOSS_EAP_VERSION.zip | grep $JBOSS_EAP_SHA256 \
    && unzip jboss-eap-$JBOSS_EAP_VERSION.zip \
    && mv $JBOSS_EAP_FOLDER /opt/jboss/ \
    && rm jboss-eap-$JBOSS_EAP_VERSION.zip \
    && chown -R jboss:0 ${JBOSS_HOME} \
    && chmod -R g+rw ${JBOSS_HOME}

# Ensure signals are forwarded to the JVM process correctly for graceful shutdown
ENV LAUNCH_JBOSS_IN_BACKGROUND true

USER jboss

# Expose the ports we're interested in
EXPOSE 8080

# Set the default command to run on boot
# This will boot WildFly in the standalone mode and bind to all interface
CMD ["$JBOSS_HOME/bin/standalone.sh", "-b", "0.0.0.0"]
