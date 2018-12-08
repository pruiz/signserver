FROM centos:7.6.1810

# Install OpenJDK JDK, Maven, Ant
RUN yum update -y \
    && yum install -y curl \
    && yum install -y java-1.7.0-openjdk java-1.7.0-openjdk-devel \
    && yum install -y maven \
    && yum install -y ant \
    && yum clean all

# Fix locale
RUN yum reinstall -q -y glibc-common \
    && localedef -i en_US -f UTF-8 en_US.UTF-8 \
    && yum clean all
