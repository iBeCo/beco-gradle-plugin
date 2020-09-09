package org.beco.config

import org.gradle.api.Plugin
import org.gradle.api.Project

class ConfigPlugin implements Plugin<Project> {
    private final static enum PluginType {
        APPLICATION([
                "android",
                "com.android.application"
        ]),
        LIBRARY([
                "android-library",
                "com.android.library"
        ]),
        FEATURE([
                "android-feature",
                "com.android.feature"
        ]),
        MODEL_APPLICATION([
                "com.android.model.application"
        ]),
        MODEL_LIBRARY([
                "com.android.model.library"
        ])

        public PluginType(Collection plugins) {
            this.plugins = plugins
        }

        private final Collection plugins

        public Collection plugins() {
            return plugins
        }
    }

    @Override
    void apply(Project project) {
        project.logger.debug('my debug message')
        for (PluginType pluginType : PluginType.values()) {
            for (String plugin : pluginType.plugins()) {
                if (project.plugins.hasPlugin(plugin)) {
                    setupPlugin(project, pluginType)
                    return
                }
            }
        }

        showWarningForPluginLocation(project)

        project.plugins.withId("android", {
            setupPlugin(project, PluginType.APPLICATION)
        })
        project.plugins.withId("android-library", {
            setupPlugin(project, PluginType.LIBRARY)
        })
        project.plugins.withId("android-feature", {
            setupPlugin(project, PluginType.FEATURE)
        })
    }

    private void showWarningForPluginLocation(Project project) {
        project.getLogger().warn(
                "Warning: Please apply plugin at the bottom of the build file"
        )
    }

    private void setupPlugin(Project project, PluginType pluginType) {
        switch (pluginType) {
            case PluginType.APPLICATION:
                project.android.applicationVariants.all {
                    variant ->
                        handleVariant(project, variant)
                }
                break
            case PluginType.LIBRARY:
                project.android.libraryVariants.all {
                    variant ->
                        handleVariant(project, variant)
                }
                break
            case PluginType.FEATURE:
                project.android.featureVariants.all {
                    variant ->
                        handleVariant(project, variant)
                }
                break
            case PluginType.MODEL_APPLICATION:
                project.model.android.applicationVariants.all {
                    variant ->
                        handleVariant(project, variant)
                }
                break
            case PluginType.MODEL_LIBRARY:
                project.model.android.libraryVariants.all {
                    variant ->
                        handleVariant(project, variant)
                }
                break
        }
    }

    private static void handleVariant(Project project, def variant) {
        File outputDir =
                project.file("$project.buildDir/generated/res/beco/$variant.dirName")

        ConfigTask task = project.tasks.create("process${variant.name.capitalize()}BecoServices",
                ConfigTask)

        task.setIntermediateDir(outputDir)
        task.setVariantDir(variant.dirName)

        if (variant.respondsTo("applicationIdTextResource")) {
            task.setPackageNameXOR2(variant.applicationIdTextResource)
            task.dependsOn(variant.applicationIdTextResource)
        } else {
            task.setPackageNameXOR1(variant.applicationId)
        }

        if (variant.respondsTo("registerGeneratedResFolders")) {
            task.ext.generatedResFolders = project.files(outputDir).builtBy(task)
            variant.registerGeneratedResFolders(task.generatedResFolders)
            if (variant.respondsTo("getMergeResourcesProvider")){
                variant.mergeResourcesProvider.configure{dependsOn(task)}
            }else{
                variant.mergeResources.dependsOn(task)
            }
        }else{
            variant.registerResGeneratingTask(task, outputDir)
        }
    }
}