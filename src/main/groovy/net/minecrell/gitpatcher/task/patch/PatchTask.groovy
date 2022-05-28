/*
 * Copyright (c) 2015, Minecrell <https://github.com/Minecrell>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package net.minecrell.gitpatcher.task.patch

import groovy.transform.CompileStatic
import net.minecrell.gitpatcher.Git
import net.minecrell.gitpatcher.task.SubmoduleTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Internal

abstract class PatchTask extends SubmoduleTask {

    @Internal
    File root

    File patchDir

    @Console
    public abstract Property<Boolean> getAddAsSafeDirectory()

    protected File[] getPatches() {
        if (!patchDir.directory) {
            return new File[0]
        }

        return patchDir.listFiles({ dir, name -> name.endsWith('.patch') } as FilenameFilter).sort()
    }

    @Internal
    File getSubmoduleRoot() {
        return new File(root, submodule)
    }

    @Internal
    File getGitDir() {
        return new File(repo, '.git')
    }

    File getRefCache() {
        return new File(gitDir, '.gitpatcher_ref')
    }

    private List<String> cachedRefs

    @CompileStatic
    private void readCache() {
        if (cachedRefs == null) {
            File refCache = this.refCache
            if (refCache.file) {
                this.cachedRefs = refCache.readLines().findResults {
                    def trimmed = it.trim()
                    !trimmed.empty && !trimmed.startsWith('#') ? trimmed : null
                }.asList().asImmutable()
            } else {
                this.cachedRefs = Collections.emptyList()
            }
        }
    }

    @Internal
    String getCachedRef() {
        readCache()
        return cachedRefs[0]
    }

    @Internal
    String getCachedSubmoduleRef() {
        readCache()
        return cachedRefs[1]
    }

    /**
     * Maybe add the configured {@code repo} as a git safe repository.
     *
     * @return whether the repo was added by us, so should be removed at the end of the task
     */
    protected boolean addAsSafeRepo(Git git) {
        if (!this.addAsSafeDirectory.get()) {
            return false
        }

        // add patched root
        // add submodule
        def safeDirs = git.config('--global', '--get-all', 'safe.directory').readText()?.split(System.lineSeparator())
        if (safeDirs != null && safeDirs.contains(git.repo.absolutePath) && safeDirs.contains(this.submoduleRoot.absolutePath)) {
            return false
        }

        git.config('--global', '--add', 'safe.directory', git.repo.absolutePath) >> null
        git.config('--global', '--add', 'safe.directory', this.submoduleRoot.absolutePath) >> null
    }

    protected void cleanUpSafeRepo(Git git) {
        git.config('--global', '--remove', 'safe.directory', git.repo.absolutePath) >> null
        git.config('--global', '--remove', 'safe.directory', this.submoduleRoot.absolutePath) >> null
    }

}
