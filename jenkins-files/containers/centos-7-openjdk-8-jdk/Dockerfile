FROM centos:7

# Install OpenJDK JDK, Maven, Ant
RUN yum update -y \
    && yum install -y curl \
    && yum install -y java-1.8.0-openjdk java-1.8.0-openjdk-devel \
    && yum install -y maven \
    && yum install -y ant \
    && yum clean all

RUN groupadd -g 1000 user
RUN useradd --uid 1000 -g 1000 user
USER user
