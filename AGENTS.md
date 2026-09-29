# Project rules

- For Android screens that display a variable-length list of apps, files, or scan results, use `RecyclerView` with an adapter and view holders. Do not build these lists by repeatedly inflating rows into a `LinearLayout` inside a `ScrollView`.
- Use generated ViewBinding classes to access views in Android activities, fragments, dialogs, and `RecyclerView` view holders. Do not add `findViewById` calls or use wrappers around `findViewById` (such as `BaseActivity.view()`) for app layout views. When modifying a screen that still uses view IDs directly, migrate that screen's view access to ViewBinding.
- When adding a color to `res/values/colors.xml`, name it `color_` followed by its uppercase eight-digit ARGB hex value, such as `<color name="color_FF42B8FF">#FF42B8FF</color>`. Check for an existing resource with the same color value and reuse it instead of adding a duplicate.
