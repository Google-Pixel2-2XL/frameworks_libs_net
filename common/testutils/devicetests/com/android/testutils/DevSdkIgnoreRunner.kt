/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.testutils

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.testutils.DevSdkIgnoreRule.IgnoreAfter
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import org.junit.runner.Description
import org.junit.runner.Runner
import org.junit.runner.manipulation.Filter
import org.junit.runner.manipulation.Filterable
import org.junit.runner.manipulation.NoTestsRemainException
import org.junit.runner.manipulation.Sortable
import org.junit.runner.manipulation.Sorter
import org.junit.runner.notification.RunNotifier
import kotlin.jvm.Throws

/**
 * A runner that can skip tests based on the development SDK as defined in [DevSdkIgnoreRule].
 *
 * Generally [DevSdkIgnoreRule] should be used for that purpose (using rules is preferable over
 * replacing the test runner), however JUnit runners inspect all methods in the test class before
 * processing test rules. This may cause issues if the test methods are referencing classes that do
 * not exist on the SDK of the device the test is run on.
 *
 * This runner inspects [IgnoreAfter] and [IgnoreUpTo] annotations on the test class, and will skip
 * the whole class if they do not match the development SDK as defined in [DevSdkIgnoreRule].
 * Otherwise, it will delegate to [AndroidJUnit4] to run the test as usual.
 *
 * Example usage:
 *
 *     @RunWith(DevSdkIgnoreRunner::class)
 *     @IgnoreUpTo(Build.VERSION_CODES.Q)
 *     class MyTestClass { ... }
 */
class DevSdkIgnoreRunner(private val klass: Class<*>) : Runner(), Filterable, Sortable {
    private val baseRunner = klass.let {
        val ignoreAfter = it.getAnnotation(IgnoreAfter::class.java)
        val ignoreUpTo = it.getAnnotation(IgnoreUpTo::class.java)

        if (isDevSdkInRange(ignoreUpTo, ignoreAfter)) AndroidJUnit4(klass) else null
    }

    override fun run(notifier: RunNotifier) {
        if (baseRunner != null) {
            baseRunner.run(notifier)
            return
        }

        // Report a single, skipped placeholder test for this class, as the class is expected to
        // report results when run. In practice runners that apply the Filterable implementation
        // would see a NoTestsRemainException and not call the run method.
        notifier.fireTestIgnored(
                Description.createTestDescription(klass, "skippedClassForDevSdkMismatch"))
    }

    override fun getDescription(): Description {
        return baseRunner?.description ?: Description.createSuiteDescription(klass)
    }

    /**
     * Get the test count before applying the [Filterable] implementation.
     */
    override fun testCount(): Int {
        // When ignoring the tests, a skipped placeholder test is reported, so test count is 1.
        return baseRunner?.testCount() ?: 1
    }

    @Throws(NoTestsRemainException::class)
    override fun filter(filter: Filter?) {
        baseRunner?.filter(filter) ?: throw NoTestsRemainException()
    }

    override fun sort(sorter: Sorter?) {
        baseRunner?.sort(sorter)
    }
}