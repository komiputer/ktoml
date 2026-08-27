/**
 * Version configuration file.
 */

package com.akuleshov7.buildutils

import org.ajoberstar.grgit.Grgit
import org.ajoberstar.reckon.gradle.ReckonExtension
import org.ajoberstar.reckon.gradle.ReckonPlugin
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

@Suppress("MISSING_KDOC_ON_FUNCTION", "MISSING_KDOC_TOP_LEVEL")
/**
 * Configures how project version is determined.
 *
 */
fun Project.configureVersioning() {
    // reckon's grgit/JGit backing fails to open a linked git worktree (`.git` is a file, not a
    // directory) and misreports it as bare; fall back to a fixed version there instead of failing
    // configuration, since release builds always run from a plain checkout.
    if (rootProject.file(".git").isFile) {
        version = "0.0.0-verified-SNAPSHOT"
        return
    }

    apply<ReckonPlugin>()

    val isSnapshot = hasProperty("reckon.stage") && property("reckon.stage") == "snapshot"
    configure<ReckonExtension> {
        setDefaultInferredScope("patch")
        if (isSnapshot) {
            snapshots()
        } else {
            stages("alpha", "rc", "final")
        }
        setScopeCalc(calcScopeFromProp().or(calcScopeFromCommitMessages()))
        setStageCalc(calcStageFromProp())
    }
}
