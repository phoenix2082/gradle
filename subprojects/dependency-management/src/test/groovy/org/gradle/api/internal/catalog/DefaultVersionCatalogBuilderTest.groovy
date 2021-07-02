/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.catalog

import com.google.common.collect.Interners
import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.catalog.problems.VersionCatalogErrorMessages
import org.gradle.api.internal.catalog.problems.VersionCatalogProblemId
import org.gradle.api.internal.catalog.problems.VersionCatalogProblemTestFor
import org.gradle.api.logging.StandardOutputListener
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.logging.services.LoggingServiceRegistry
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.util.function.Supplier

class DefaultVersionCatalogBuilderTest extends Specification implements VersionCatalogErrorMessages {

    @Subject
    DefaultVersionCatalogBuilder builder = new DefaultVersionCatalogBuilder("libs", Interners.newStrongInterner(), Interners.newStrongInterner(), TestUtil.objectFactory(), TestUtil.providerFactory(), Stub(Supplier))

    @VersionCatalogProblemTestFor(
        VersionCatalogProblemId.INVALID_DEPENDENCY_NOTATION
    )
    @Unroll("#notation is an invalid notation")
    def "reasonable error message if notation is invalid"() {
        when:
        builder.alias("foo").to(notation)

        then:
        InvalidUserDataException ex = thrown()
        verify(ex.message, invalidDependencyNotation {
            inCatalog('libs')
            usingSettingsApi()
            invalidNotation(notation)
            alias('foo')
        })
        where:
        notation << ["", "a", "a:", "a:b", ":b", "a:b:", ":::", "a:b:c:d"]
    }

    @VersionCatalogProblemTestFor(
        VersionCatalogProblemId.INVALID_ALIAS_NOTATION
    )
    @Unroll("#notation is an invalid alias")
    def "reasonable error message if alias is invalid"() {
        when:
        builder.alias(notation).to("org:foo:1.0")

        then:
        InvalidUserDataException ex = thrown()
        verify(ex.message, invalidAliasNotation {
            inCatalog('libs')
            invalidNotation(notation)
        })

        where:
        notation << ["", "a", "1a", "A", "Aa", "abc\$", "abc&"]
    }

    @VersionCatalogProblemTestFor(
        VersionCatalogProblemId.RESERVED_ALIAS_NAME
    )
    @Unroll
    def "forbids using #name as a dependency alias"() {
        when:
        builder.alias(name).to("org:foo:1.0")

        then:
        InvalidUserDataException ex = thrown()
        verify(ex.message, reservedAlias {
            inCatalog('libs')
            alias(name).shouldNotEndWith(suffix)
            reservedAliasSuffix('bundle', 'bundles', 'version', 'versions', 'plugin', 'plugins')
        })

        where:
        name          | suffix
        "bundles"     | "bundles"
        "versions"    | "versions"
        "plugins"     | "plugins"
        "fooBundle"   | "bundle"
        "fooVersion"  | "version"
        "fooPlugin"   | "plugin"
        "foo.plugin"  | "plugin"
        "foo.bundle"  | "bundle"
        "foo.version" | "version"
    }

    @VersionCatalogProblemTestFor(
        VersionCatalogProblemId.INVALID_ALIAS_NOTATION
    )
    @Unroll("#notation is an invalid bundle name")
    def "reasonable error message if bundle name is invalid"() {
        when:
        builder.bundle(notation, [])

        then:
        InvalidUserDataException ex = thrown()
        verify(ex.message, invalidAliasNotation {
            inCatalog('libs')
            kind('bundle')
            invalidNotation(notation)
        })

        where:
        notation << ["", "a", "1a", "A", "Aa", "abc\$", "abc&"]
    }

    def "warns if multiple entries use the same alias"() {
        StandardOutputListener listener = Mock()
        def registry = LoggingServiceRegistry.newCommandLineProcessLogging()
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        loggingManager.enableUserStandardOutputListeners()
        loggingManager.addStandardOutputListener(listener)
        loggingManager.start()

        builder.alias("foo").to("a:b:1.0")

        when:
        builder.alias("foo").to("e:f:1.1")

        then:
        1 * listener.onOutput("Duplicate entry for alias 'foo': dependency {group='a', name='b', version='1.0'} is replaced with dependency {group='e', name='f', version='1.1'}")

        cleanup:
        loggingManager.stop()
    }

    def "warns if multiple entries use the same bundle name"() {
        StandardOutputListener listener = Mock()
        def registry = LoggingServiceRegistry.newCommandLineProcessLogging()
        def loggingManager = registry.newInstance(LoggingManagerInternal)
        loggingManager.enableUserStandardOutputListeners()
        loggingManager.addStandardOutputListener(listener)
        loggingManager.start()

        builder.bundle("foo", ["a", "b"])

        when:
        builder.bundle("foo", ["c", "d", "e"])

        then:
        1 * listener.onOutput("Duplicate entry for bundle 'foo': [a, b] is replaced with [c, d, e]")

        cleanup:
        loggingManager.stop()
    }

    @VersionCatalogProblemTestFor(
        VersionCatalogProblemId.UNDEFINED_ALIAS_REFERENCE
    )
    def "fails building the model if a bundle references a non-existing alias"() {
        builder.alias("guava").to("com.google.guava:guava:17.0")
        builder.bundle("toto", ["foo"])

        when:
        builder.build()

        then:
        InvalidUserDataException ex = thrown()
        verify(ex.message, undefinedAliasRef {
            inCatalog('libs')
            aliasRef('foo')
            bundle('toto')
        })
    }

    def "normalizes alias separators to dot"() {
        builder.alias("guava").to("com.google.guava:guava:17.0")
        builder.alias("groovy").to("org.codehaus.groovy", "groovy").version {
            it.strictly("3.0.5")
        }
        builder.alias("groovy-json").to("org.codehaus.groovy", "groovy-json").version {
            it.prefer("3.0.5")
        }
        builder.bundle("groovy", ["groovy", "groovy-json"])

        when:
        def model = builder.build()

        then:
        model.bundleAliases == ["groovy"]
        model.getBundle("groovy").components == ["groovy", "groovy.json"]

        model.dependencyAliases == ["groovy", "groovy.json", "guava"]
        model.getDependencyData("guava").version.requiredVersion == '17.0'
        model.getDependencyData("groovy").version.strictVersion == '3.0.5'
        model.getDependencyData("groovy-json").version.strictVersion == ''
        model.getDependencyData("groovy.json").version.strictVersion == ''
        model.getDependencyData("groovy-json").version.preferredVersion == '3.0.5'
        model.getDependencyData("groovy.json").version.preferredVersion == '3.0.5'
        model.getDependencyData("groovy_json").version.preferredVersion == '3.0.5'
    }

    def "can use arbitrary separators when building bundles"() {
        builder.alias("foo.bar").to("foo:bar:1.0")
        builder.alias("foo-baz").to("foo:baz:1.0")
        builder.alias("foo_qux").to("foo:qux:1.0")

        builder.bundle("my", ["foo-bar", "foo_baz", "foo.qux"])
        builder.bundle("a.b", ["foo.bar"])
        builder.bundle("a_c", ["foo.bar"])
        builder.bundle("a-d", ["foo.bar"])

        when:
        def model = builder.build()

        then:
        model.dependencyAliases == ["foo.bar", "foo.baz", "foo.qux"]
        model.bundleAliases == ["a.b", "a.c", "a.d", "my"]
        model.getBundle("my").components == ["foo.bar", "foo.baz", "foo.qux"]

        model.getBundle("a.b").is(model.getBundle("a-b"))
        model.getBundle("a.b").is(model.getBundle("a_b"))
    }

    def "can use arbitrary separators when referencing versions"() {
        builder.version("my-v1", "1.0")
        builder.version("my_v2", "1.0")
        builder.version("my.v3") {
            it.prefer("1.0")
        }
        builder.alias("foo").to("org", "foo").versionRef("my.v1")
        builder.alias("bar").to("org", "foo").versionRef("my-v2")
        builder.alias("baz").to("org", "foo").versionRef("my_v3")

        when:
        def model = builder.build()

        then:
        model.versionAliases == ["my.v1", "my.v2", "my.v3"]
        model.getDependencyData("foo").versionRef == "my.v1"
        model.getDependencyData("bar").versionRef == "my.v2"
        model.getDependencyData("baz").versionRef == "my.v3"
    }

    def "can use rich versions in short-hand notation"() {
        builder.alias("dummy").to("g:a:1.5!!")
        builder.alias("alias").to("g:a:[1.0,2.0[!!1.7")

        when:
        def model = builder.build()

        then:
        model.dependencyAliases == ["alias", "dummy"]
        model.getDependencyData("dummy").version.strictVersion == '1.5'
        model.getDependencyData("alias").version.strictVersion == '[1.0,2.0['
        model.getDependencyData("alias").version.preferredVersion == '1.7'
    }

    def "strings are interned"() {
        builder.alias("foo").to("bar", "baz").version {
            it.require "1.0"
        }
        builder.alias("baz").to("foo", "bar").version {
            it.prefer "1.0"
        }
        when:
        def model = builder.build()

        then:
        def bazKey = model.dependencyAliases.find { it == 'baz' }
        model.getDependencyData("foo").group.is(model.getDependencyData("baz").name)
        model.getDependencyData("foo").name.is(bazKey)
        model.getDependencyData("foo").version.requiredVersion.is(model.getDependencyData("baz").version.preferredVersion)
    }

    def "can create an alias to a referenced version"() {
        builder.version("ver", "1.7!!")
        builder.alias("foo").to("org", "foo").versionRef("ver")

        when:
        def model = builder.build()

        then:
        model.getDependencyData("foo").version.strictVersion == "1.7"
    }

    def "can declare a plugin with a version"() {
        builder.alias("my").toPluginId("org.plugin").version("1.3")

        when:
        def model = builder.build()

        then:
        model.getPlugin("my").version.requiredVersion == "1.3"
    }

    def "can declare a plugin referencing a version"() {
        builder.version("ver", "1.5")
        builder.alias("my").toPluginId("org.plugin").versionRef("ver")

        when:
        def model = builder.build()

        then:
        model.getPlugin("my").version.requiredVersion == "1.5"
    }

    def "can create an alias with an empty version"() {
        builder.alias("foo").to("org", "foo").withoutVersion()

        when:
        def model = builder.build()

        then:
        model.getDependencyData("foo").version.requiredVersion == ""
    }

    @VersionCatalogProblemTestFor(
        VersionCatalogProblemId.UNDEFINED_VERSION_REFERENCE
    )
    def "reasonable error message if referenced version doesn't exist"() {
        builder.alias("foo").to("org", "foo").versionRef("nope")
        builder.version('v1', '1.0')
        builder.version('v2', '1.2')
        when:
        builder.build()

        then:
        InvalidUserDataException ex = thrown()
        verify(ex.message, undefinedVersionRef {
            inCatalog('libs')
            forDependency('org', 'foo')
            versionRef('nope')
            existing('v1', 'v2')
        })
    }
}
