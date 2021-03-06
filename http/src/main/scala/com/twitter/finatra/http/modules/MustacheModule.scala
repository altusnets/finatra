package com.twitter.finatra.http.modules

import com.github.mustachejava.{DefaultMustacheFactory, Mustache, MustacheFactory}
import com.google.inject.{Module, Provides}
import com.twitter.finatra.http.internal.marshalling.mustache.ScalaObjectHandler
import com.twitter.finatra.modules.{FileResolverFlags, FileResolverModule}
import com.twitter.finatra.utils.FileResolver
import com.twitter.inject.TwitterModule
import com.twitter.inject.annotations.Flag
import java.io._
import javax.inject.Singleton

object MustacheModule extends TwitterModule {

  private val templatesDir =
    flag("mustache.templates.dir", "templates", "templates resource directory")

  override def modules: Seq[Module] = Seq(FileResolverModule, MessageBodyModule)

  @Provides
  @Singleton
  def provideMustacheFactory(
    resolver: FileResolver,
    @Flag(FileResolverFlags.LocalDocRoot) localDocRoot: String
  ): MustacheFactory = {
    // templates are cached only if there is no local.doc.root
    val cacheMustacheTemplates = localDocRoot.isEmpty
    val templatesDirectory = templatesDir()

    new DefaultMustacheFactory(templatesDirectory) {
      setObjectHandler(new ScalaObjectHandler)

      override def compile(name: String): Mustache = {
        if (cacheMustacheTemplates) {
          super.compile(name)
        } else {
          new LocalFilesystemDefaultMustacheFactory(templatesDirectory, resolver).compile(name)
        }
      }
    }
  }
}

/**
 * A local filesystem-only MustacheFactory. Uses the FileResolver for resolution and
 * does not internally cache templates.
 */
private final class LocalFilesystemDefaultMustacheFactory(
  templatesDirectory: String,
  resolver: FileResolver)
    extends DefaultMustacheFactory {
  setObjectHandler(new ScalaObjectHandler)

  override def getReader(resourceName: String): Reader = {
    // Relative paths are prefixed by the templates directory.
    val filepath = if (resourceName.startsWith("/")) {
      resourceName
    } else if (templatesDirectory.startsWith("/")) {
      s"$templatesDirectory/$resourceName"
    } else {
      s"/$templatesDirectory/$resourceName"
    }

    (resolver.getInputStream(filepath) map { inputStream: InputStream =>
      new InputStreamReader(inputStream)
    }).getOrElse(throw new FileNotFoundException(s"Unable to find file: $filepath"))
  }
}
