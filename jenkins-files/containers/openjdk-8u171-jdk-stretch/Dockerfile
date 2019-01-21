FROM openjdk:8u171-jdk-stretch

# Install Maven and Ant
RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive \
    apt-get install -y maven ant \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/*  

ENV JAVA_OPTS="-Xms256M -Xmx256M"
ENV ANT_OPTS="-Xms64M -Xmx64M"

RUN groupadd -g 1000 user
RUN useradd --uid 1000 -g 1000 user
USER user
