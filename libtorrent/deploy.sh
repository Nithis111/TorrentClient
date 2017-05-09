#!/bin/bash

mvn gpg:sign-and-deploy-file -DpomFile=pom.xml -Dfile=libtorrent.aar \
  -DrepositoryId=sonatype-nexus-staging \
  -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/

mvn install:install-file -DpomFile=pom.xml -Dfile=libtorrent.aar \
  -DrepositoryId=sonatype-nexus-staging \
  -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/
