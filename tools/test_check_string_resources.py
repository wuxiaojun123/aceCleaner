import tempfile
from pathlib import Path
import unittest

from check_string_resources import check, MODULES, translation_coverage


class StringResourceCheckTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for module in MODULES:
            (self.root / module / "src/main").mkdir(parents=True)

    def write(self, relative, text):
        path = self.root / "app/src" / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def problems(self):
        return check(self.root)[0]

    def test_localized_and_styled_resources_are_allowed(self):
        self.write("main/res/values/strings.xml", '<resources><string name="string_title"><b>Hello</b></string></resources>')
        self.write("main/res/values-ja/strings.xml", '<resources><string name="string_title">こんにちは</string></resources>')
        self.assertFalse(self.problems())

    def test_split_string_files_and_duplicates_fail(self):
        self.write("main/res/values/strings.xml", '<resources><string name="string_title">Hello</string></resources>')
        self.write("main/res/values/strings_more.xml", '<resources><string name="string_title">Hi</string></resources>')
        self.assertEqual(2, len(self.problems()))

    def test_array_and_plural_resources_must_be_centralized(self):
        self.write("main/res/values/more.xml", '<resources><string-array name="names"><item>A</item></string-array><plurals name="count"><item quantity="other">%d items</item></plurals></resources>')
        self.assertEqual(2, len(self.problems()))

    def test_string_aliases_share_the_string_namespace(self):
        self.write("main/res/values/strings.xml", '<resources><string name="string_title">Hi</string><item name="string_title" type="string">@string/string_other</item></resources>')
        self.assertEqual(1, len(self.problems()))

    def test_missing_translation_report_excludes_nontranslatable_values(self):
        self.write("main/res/values/strings.xml", '<resources><string name="string_title">Hi</string><string name="string_other">Other</string><string name="string_unit" translatable="false">MB</string></resources>')
        self.write("main/res/values-ja/strings.xml", '<resources><string name="string_title">こんにちは</string></resources>')
        coverage = translation_coverage(self.root)["app"]
        self.assertEqual(2, coverage["translatable_count"])
        self.assertEqual(["string_other"], coverage["missing_by_configuration"]["values-ja"])

    def test_style_and_manifest_literals_fail(self):
        self.write("main/res/values/styles.xml", '<resources><style name="Title"><item name="android:text">Hello</item></style></resources>')
        self.write("main/AndroidManifest.xml", '<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:label="Cleaner"/></manifest>')
        self.assertEqual(2, len(self.problems()))

    def test_xml_numbers_and_accessibility_literals_fail(self):
        self.write("main/res/layout/card.xml", '<TextView xmlns:android="http://schemas.android.com/apk/res/android" android:text="0" android:contentDescription="Close"/>')
        self.assertEqual(2, len(self.problems()))

    def test_resource_references_and_tools_previews_are_allowed(self):
        self.write("main/res/layout/card.xml", '<TextView xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools" android:text="@string/string_title" android:hint="?attr/title" tools:text="Preview"/>')
        self.assertFalse(self.problems())

    def test_malformed_xml_and_missing_sources_fail(self):
        self.write("main/res/values/strings.xml", '<resources>')
        self.assertTrue(self.problems())
        missing, _ = check(self.root / "missing")
        self.assertEqual(len(MODULES), len(missing))

    def test_kotlin_java_and_multiline_ui_calls_fail(self):
        self.write("main/java/Screen.kt", '''binding.title.text = "Hello"
            Toast.makeText(this,
                "Message", Toast.LENGTH_SHORT)
            builder.setPositiveButton("OK") { _, _ -> }
            notification.setContentText("News")
            binding.hint = "Search"
        ''')
        self.write("main/java/Screen.java", 'builder.setTitle("Title");')
        self.assertEqual(6, len(self.problems()))

    def test_comments_protocols_tests_and_resource_calls_are_allowed(self):
        self.write("main/java/Screen.kt", '''// title.text = "Sample"
            /* Toast.makeText(this, "Sample", 0) */
            LogUtils.d("Message", "TAG")
            val key = "settings_key"
            val url = "https://example.com"
            title.text = getString(R.string.string_title)
            title.text = ""
            builder.setTitle(R.string.string_title)
        ''')
        self.write("test/java/ScreenTest.kt", 'view.text = "Test fixture"')
        self.assertFalse(self.problems())


if __name__ == "__main__":
    unittest.main()
