FROM centos:centos7

# update, install some rpms for docker and then some for emissary and cleanup
RUN yum install -y epel-release && yum update -y && yum clean all -y && \
    yum install -y docker-io unzip wget tar which man && \
    yum install -y expect perl && \
    rm -rf /var/cache/yum

# setup Oracle Java
# http://download.oracle.com/otn-pub/java/jdk/8u172-b11/a58eab1ec242421181065cdc37240b08/jdk-8u172-linux-x64.tar.gz
# http://download.oracle.com/otn-pub/java/jdk/8u172-b11/jdk-8u172-linux-x64.tar.gz?AuthParam=1526001044_f1da8f48952dabecba40bb4c98d928d7
ENV JAVA64=/usr/java64 \
    JAVA_HOME=${JAVA64}/current \
    JDK_HOME=${JAVA_HOME} \
    PATH=${JAVA_HOME}/bin:${PATH}
RUN mkdir ${JAVA64} && cd ${JAVA64} && \
     wget --no-check-certificate \
     --no-cookies \
     --header "Cookie: gpw_e24=http%3A%2F%2Fwww.oracle.com%2F; oraclelicense=accept-securebackup-cookie" \
     http://download.oracle.com/otn-pub/java/jdk/8u172-b11/a58eab1ec242421181065cdc37240b08/jdk-8u172-linux-x64.tar.gz && \
     cd ${JAVA64} && tar -xzf jdk-8u172-linux-x64.tar.gz && rm jdk-8u172-linux-x64.tar.gz && \
     cd ${JAVA64} && ln -s 1.8.0_172 current && \
     chown -R root:root ${JAVA64} && \
     chmod -R a+rwX ${JAVA64}

# setup Maven
ENV MAVEN_VERSION=3.5.3 \
    MAVEN_OPTS="-Xms512M -Xmx1024M -Xss1M -XX:MaxPermSize=128M -Djava.awt.headless=true" \
    PATH=/usr/share/apache-maven-${MAVEN_VERSION}/bin:${PATH}
RUN curl http://mirrors.ibiblio.org/apache/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz > /usr/share/maven.tar.gz && \
    cd /usr/share && \
    tar xvzf maven.tar.gz && \
    rm -f maven.tar.gz && \
    mkdir /root/.m2

CMD /bin/bash
