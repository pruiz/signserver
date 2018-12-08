FROM openjdk:8-jdk-stretch

# Install Maven and Ant
RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive \
    apt-get install -y maven ant \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/*  
