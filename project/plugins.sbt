addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.10.2")

resolvers += Resolver.url("artifactory", url("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.typesafe.sbt" % "sbt-start-script" % "0.10.0")