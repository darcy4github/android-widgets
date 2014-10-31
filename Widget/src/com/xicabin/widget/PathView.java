package com.xicabin.widget;

import java.io.File;
import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

/**
 * Auth path completion edit view<br/>
 * Usage: <br/>
 *
 * <pre>
 * <code>
 * 		final PathView editor = new PathView(this);
 * 		editor.setBackgroundResource(0);
 * 		editor.setLines(1);
 * 		editor.setSingleLine(true);
 * 		editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16F);
 * 		editor.setPadding(16,16, 16, 16);
 * 		editor.setText(currentPath.getAbsolutePath());
 * 		editor.setSelectAllOnFocus(true);
 * 		editor.setImeOptions(EditorInfo.IME_ACTION_NEXT);
 *
 * 		AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
 * 		builder.setView(editor);
 * 		builder.setTitle(R.string.move_to);
 * 		builder.setCancelable(true);
 * 		builder.setNegativeButton(R.string.cancel, null);
 * 		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
 *
 * 			public void onClick(DialogInterface dialog, int which) {
 * 				String newPath = editor.getText().toString();
 * 				if (TextUtils.isEmpty(newPath)) {
 * 					return;
 * 				}
 * 				moveFile(currentPath, new File(newPath));
 * 			}
 * 		});
 *
 * 		final AlertDialog dialog = builder.create();
 *
 * 		editor.setOnEditorActionListener(new OnEditorActionListener() {
 *
 * 			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
 * 				if (actionId == EditorInfo.IME_ACTION_NEXT) {
 * 					editor.completePath();
 * 					return true;
 * 				}
 * 				return false;
 * 			}
 * 		});
 *
 * 		// Show keyboard on shown
 * 		Window window = dialog.getWindow();
 * 		window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
 * 				| WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
 * 		window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
 *
 * 		dialog.show(); </code>
 * </pre>
 *
 * @author Darcy
 * @version 1.0.1
 */
public class PathView extends EditText {
	private PathInfo mCurrentPath;
	private PathElementSpan mCurrentSpan;

	public PathView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initialize();
	}

	public PathView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initialize();
	}

	public PathView(Context context) {
		super(context);
		initialize();
	}

	private void initialize() {
		setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_URI);
		setFilters(new InputFilter[] { new InputFilter() {

			@Override
			public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
				// If the 'space' key is pressed inside a Path Element
				PathElementSpan span = mCurrentSpan;
				if (end == start + 1 && source.charAt(start) == ' ' && span != null
						&& dest.getSpanStart(span) <= dstart && dest.getSpanEnd(span) == dend) {
					return dest.subSequence(dstart, dend);
				}
				return null;
			}
		} });
	}

	@Override
	protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
		super.onTextChanged(text, start, lengthBefore, lengthAfter);
		if (mCurrentSpan != null) {
			PathElementSpan span = mCurrentSpan;
			mCurrentSpan = null;
			getText().removeSpan(span);
		}
	}

	public void completePath() {
		Editable text = getEditableText();

		// Remove old path element
		if (mCurrentSpan != null) {
			PathElementSpan span = mCurrentSpan;
			mCurrentSpan = null;
			text.removeSpan(span);
		}

		// Get current path
		PathInfo pi = getPathInfo();
		if (pi == null) {
			return;
		}

		int prefixStart = pi.getPathLength();
		int prefixEnd = getSelectionStart();
		if (prefixEnd < prefixStart) {
			prefixEnd = text.length();
		}
		String prefix = text.subSequence(prefixStart, prefixEnd).toString();

		// Get next item matched the prefix
		String nextPath = pi.next(prefix);
		if (nextPath == null) {
			return;
		}

		int replaceStart = prefixStart;
		int replaceEnd = getSelectionEnd();
		if (replaceEnd < replaceStart) {
			replaceEnd = replaceStart;
		}
		text.replace(replaceStart, replaceEnd, nextPath);

		// Set text span
		int pathStart = prefixStart;
		int pathEnd = pathStart + nextPath.length();
		mCurrentSpan = new PathElementSpan();
		text.setSpan(mCurrentSpan, pathStart, pathEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

		// Set text selection
		int selectionStart = prefixEnd;
		int selectionEnd = prefixStart + nextPath.length();
		setSelection(selectionStart, selectionEnd);
	}

	protected PathInfo getPathInfo() {
		int end = getSelectionStart();
		if (end < 0) {
			end = getText().length();
		}
		String path = getText().subSequence(0, end).toString();
		if (path.isEmpty()) {
			if (mCurrentPath == null || !mCurrentPath.isPathEmpty()) {
				mCurrentPath = PathInfo.from(null);
			}
			return mCurrentPath;
		}
		// Get parent directory from path
		File directory = null;
		if (path.endsWith(File.separator)) {
			// Path is a directory
			directory = new File(path);
		} else {
			// Assume path is a file
			directory = new File(path).getParentFile();
		}
		if (directory == null || !directory.exists()) {
			return null;
		}
		// Create path info
		if (mCurrentPath == null || !mCurrentPath.equals(directory)) {
			mCurrentPath = PathInfo.from(directory);
		}
		return mCurrentPath;
	}

	public static class PathInfo {
		private static Comparator<String> CMPR = new Comparator<String>() {
			Collator COLLATOR = Collator.getInstance();

			@Override
			public int compare(String lhs, String rhs) {
				return COLLATOR.compare(lhs, rhs);
			}
		};

		private String _path;
		private String[] _children;
		private int _index;

		protected PathInfo() {
		}

		public boolean isPathEmpty() {
			return _path.isEmpty();
		}

		public int getPathLength() {
			return _path.length();
		}

		public String next(String prefix) {
			if (_children.length == 0) {
				return null;
			}
			if (TextUtils.isEmpty(prefix)) {
				if (++_index >= _children.length) {
					_index = 0;
				}
				return _children[_index];
			}
			String prefixLower = prefix.toLowerCase(Locale.getDefault());
			String result = null;
			int ii = 0;
			while (ii++ < _children.length) {
				// Wrap search
				if (++_index >= _children.length) {
					_index = 0;
				}
				if (_children[_index].toLowerCase(Locale.getDefault()).startsWith(prefixLower)) {
					result = _children[_index];
					break;
				}
			}
			return result;
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof PathInfo) {
				PathInfo a = (PathInfo) o;
				return _path.equals(a._path) && Arrays.equals(_children, a._children);
			} else if (o instanceof File) {
				String anotherPath = ((File) o).getAbsolutePath();
				if (!anotherPath.endsWith(File.separator)) {
					anotherPath = anotherPath + File.separator;
				}
				return _path.equals(anotherPath);
			}
			return false;
		}

		public static PathInfo from(File path) {
			PathInfo pi = new PathInfo();
			if (path == null) {
				pi._path = "";

				File[] roots = File.listRoots();
				if (roots != null && roots.length > 0) {
					pi._children = new String[roots.length];
					for (int ii = 0; ii < roots.length; ++ii) {
						pi._children[ii] = roots[ii].getAbsolutePath();
						if (!pi._children[ii].endsWith(File.separator) && roots[ii].isDirectory()) {
							pi._children[ii] = pi._children[ii] + File.separator;
						}
					}
				} else {
					pi._children = new String[0];
				}
			} else {
				pi._path = path.getAbsolutePath();
				if (!pi._path.endsWith(File.separator)) {
					pi._path = pi._path + File.separator;
				}

				pi._children = path.list();
				if (pi._children == null) {
					pi._children = new String[0];
				}
				for (int ii = 0; ii < pi._children.length; ++ii) {
					if (!pi._children[ii].endsWith(File.separator)
							&& new File(pi._path, pi._children[ii]).isDirectory()) {
						pi._children[ii] = pi._children[ii] + File.separator;
					}
				}
			}
			Arrays.sort(pi._children, CMPR);
			return pi;
		}
	}

	public static class PathElementSpan extends UnderlineSpan {
	}
}
