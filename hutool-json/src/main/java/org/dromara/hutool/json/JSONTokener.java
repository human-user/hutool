/*
 * Copyright (c) 2023 looly(loolly@aliyun.com)
 * Hutool is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 *          http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
 * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
 * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */

package org.dromara.hutool.json;

import org.dromara.hutool.core.io.IoUtil;
import org.dromara.hutool.core.io.ReaderWrapper;
import org.dromara.hutool.core.text.StrUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;

/**
 * JSON解析器，用于将JSON字符串解析为JSONObject或者JSONArray
 *
 * @author from JSON.org
 */
public class JSONTokener extends ReaderWrapper {

	private long character;
	/**
	 * 是否结尾 End of stream
	 */
	private boolean eof;
	/**
	 * 在Reader的位置（解析到第几个字符）
	 */
	private long index;
	/**
	 * 当前所在行
	 */
	private long line;
	/**
	 * 前一个字符
	 */
	private char previous;
	/**
	 * 是否使用前一个字符
	 */
	private boolean usePrevious;

	/**
	 * JSON配置
	 */
	private final JSONConfig config;

	// ------------------------------------------------------------------------------------ Constructor start
	/**
	 * 从InputStream中构建，使用UTF-8编码
	 *
	 * @param inputStream InputStream
	 * @param config      JSON配置
	 * @throws JSONException JSON异常，包装IO异常
	 */
	public JSONTokener(final InputStream inputStream, final JSONConfig config) throws JSONException {
		this(IoUtil.toUtf8Reader(inputStream), config);
	}

	/**
	 * 从字符串中构建
	 *
	 * @param s      JSON字符串
	 * @param config JSON配置
	 */
	public JSONTokener(final CharSequence s, final JSONConfig config) {
		this(new StringReader(StrUtil.str(s)), config);
	}

	/**
	 * 从Reader中构建
	 *
	 * @param reader Reader
	 * @param config JSON配置
	 */
	public JSONTokener(final Reader reader, final JSONConfig config) {
		super(IoUtil.toMarkSupport(reader));
		this.eof = false;
		this.usePrevious = false;
		this.previous = 0;
		this.index = 0;
		this.character = 1;
		this.line = 1;
		this.config = config;
	}
	// ------------------------------------------------------------------------------------ Constructor end

	/**
	 * 将标记回退到第一个字符，重新开始解析新的JSON
	 *
	 * @throws JSONException JSON异常，包装IO异常
	 */
	public void back() throws JSONException {
		if (this.usePrevious || this.index <= 0) {
			throw new JSONException("Stepping back two steps is not supported");
		}
		this.index -= 1;
		this.character -= 1;
		this.usePrevious = true;
		this.eof = false;
	}

	/**
	 * @return 是否进入结尾
	 */
	public boolean end() {
		return this.eof && !this.usePrevious;
	}

	/**
	 * 源字符串是否有更多的字符
	 *
	 * @return 如果未达到结尾返回true，否则false
	 * @throws JSONException JSON异常，包装IO异常
	 */
	public boolean more() throws JSONException {
		this.next();
		if (this.end()) {
			return false;
		}
		this.back();
		return true;
	}

	/**
	 * 获得源字符串中的下一个字符
	 *
	 * @return 下一个字符, or 0 if past the end of the source string.
	 * @throws JSONException JSON异常，包装IO异常
	 */
	public char next() throws JSONException {
		int c;
		if (this.usePrevious) {
			this.usePrevious = false;
			c = this.previous;
		} else {
			try {
				c = read();
			} catch (final IOException exception) {
				throw new JSONException(exception);
			}

			if (c <= 0) { // End of stream
				this.eof = true;
				c = 0;
			}
		}
		this.index += 1;
		if (this.previous == '\r') {
			this.line += 1;
			this.character = c == '\n' ? 0 : 1;
		} else if (c == '\n') {
			this.line += 1;
			this.character = 0;
		} else {
			this.character += 1;
		}
		this.previous = (char) c;
		return this.previous;
	}

	/**
	 * Get the last character read from the input or '\0' if nothing has been read yet.
	 *
	 * @return the last character read from the input.
	 */
	protected char getPrevious() {
		return this.previous;
	}

	/**
	 * 读取下一个字符，并比对是否和指定字符匹配
	 *
	 * @param c 被匹配的字符
	 * @return The character 匹配到的字符
	 * @throws JSONException 如果不匹配抛出此异常
	 */
	public char next(final char c) throws JSONException {
		final char n = this.next();
		if (n != c) {
			throw this.syntaxError("Expected '" + c + "' and instead saw '" + n + "'");
		}
		return n;
	}

	/**
	 * 获得接下来的n个字符
	 *
	 * @param n 字符数
	 * @return 获得的n个字符组成的字符串
	 * @throws JSONException 如果源中余下的字符数不足以提供所需的字符数，抛出此异常
	 */
	public String next(final int n) throws JSONException {
		if (n == 0) {
			return "";
		}

		final char[] chars = new char[n];
		int pos = 0;
		while (pos < n) {
			chars[pos] = this.next();
			if (this.end()) {
				throw this.syntaxError("Substring bounds error");
			}
			pos += 1;
		}
		return new String(chars);
	}

	/**
	 * 获得下一个字符，跳过空白符
	 *
	 * @return 获得的字符，0表示没有更多的字符
	 * @throws JSONException 获得下一个字符时抛出的异常
	 */
	public char nextClean() throws JSONException {
		char c;
		while (true) {
			c = this.next();
			if (c == 0 || c > ' ') {
				return c;
			}
		}
	}

	/**
	 * 返回当前位置到指定引号前的所有字符，反斜杠的转义符也会被处理。<br>
	 * 标准的JSON是不允许使用单引号包含字符串的，但是此实现允许。
	 *
	 * @param quote 字符引号, 包括 {@code "}（双引号） 或 {@code '}（单引号）。
	 * @return 截止到引号前的字符串
	 * @throws JSONException 出现无结束的字符串时抛出此异常
	 */
	public String nextString(final char quote) throws JSONException {
		char c;
		final StringBuilder sb = new StringBuilder();
		while (true) {
			c = this.next();
			switch (c) {
				case 0:
					throw this.syntaxError("Unterminated string");
				case '\n':
				case '\r':
					//throw this.syntaxError("Unterminated string");
					// https://gitee.com/dromara/hutool/issues/I76CSU
					// 兼容非转义符
					sb.append(c);
					break;
				case '\\':// 转义符
					c = this.next();
					switch (c) {
						case 'b':
							sb.append('\b');
							break;
						case 't':
							sb.append('\t');
							break;
						case 'n':
							sb.append('\n');
							break;
						case 'f':
							sb.append('\f');
							break;
						case 'r':
							sb.append('\r');
							break;
						case 'u':// Unicode符
							sb.append((char) Integer.parseInt(this.next(4), 16));
							break;
						case '"':
						case '\'':
						case '\\':
						case '/':
							sb.append(c);
							break;
						default:
							throw this.syntaxError("Illegal escape.");
					}
					break;
				default:
					if (c == quote) {
						return sb.toString();
					}
					sb.append(c);
			}
		}
	}

	/**
	 * 获得从当前位置直到分隔符（不包括分隔符）或行尾的的所有字符。
	 *
	 * @param delimiter 分隔符
	 * @return 字符串
	 * @throws JSONException JSON异常，包装IO异常
	 */
	public String nextTo(final char delimiter) throws JSONException {
		final StringBuilder sb = new StringBuilder();
		for (; ; ) {
			final char c = this.next();
			if (c == delimiter || c == 0 || c == '\n' || c == '\r') {
				if (c != 0) {
					this.back();
				}
				return sb.toString().trim();
			}
			sb.append(c);
		}
	}

	/**
	 * Get the text up but not including one of the specified delimiter characters or the end of line, whichever comes first.
	 *
	 * @param delimiters A set of delimiter characters.
	 * @return A string, trimmed.
	 * @throws JSONException JSON异常，包装IO异常
	 */
	public String nextTo(final String delimiters) throws JSONException {
		char c;
		final StringBuilder sb = new StringBuilder();
		for (; ; ) {
			c = this.next();
			if (delimiters.indexOf(c) >= 0 || c == 0 || c == '\n' || c == '\r') {
				if (c != 0) {
					this.back();
				}
				return sb.toString().trim();
			}
			sb.append(c);
		}
	}

	/**
	 * 获得下一个值，值类型可以是Boolean, Double, Integer, JSONArray, JSONObject, Long, or String
	 *
	 * @return Boolean, Double, Integer, JSONArray, JSONObject, Long, or String
	 * @throws JSONException 语法错误
	 */
	public Object nextValue() throws JSONException {
		char c = this.nextClean();
		final String string;

		switch (c) {
			case '"':
			case '\'':
				return this.nextString(c);
			case '{':
				this.back();
				try {
					return new JSONObject(this, this.config);
				} catch (final StackOverflowError e) {
					throw new JSONException("JSONObject depth too large to process.", e);
				}
			case '[':
				this.back();
				try {
					return new JSONArray(this, this.config);
				} catch (final StackOverflowError e) {
					throw new JSONException("JSONArray depth too large to process.", e);
				}
		}

		/*
		 * Handle unquoted text. This could be the values true, false, or null, or it can be a number.
		 * An implementation (such as this one) is allowed to also accept non-standard forms. Accumulate
		 * characters until we reach the end of the text or a formatting character.
		 */

		final StringBuilder sb = new StringBuilder();
		while (c >= ' ' && ",:]}/\\\"[{;=#".indexOf(c) < 0) {
			sb.append(c);
			c = this.next();
		}
		this.back();

		string = sb.toString().trim();
		if (0 == string.length()) {
			throw this.syntaxError("Missing value");
		}
		return InternalJSONUtil.stringToValue(string);
	}

	/**
	 * Skip characters until the next character is the requested character. If the requested character is not found, no characters are skipped. 在遇到指定字符前，跳过其它字符。如果字符未找到，则不跳过任何字符。
	 *
	 * @param to 需要定位的字符
	 * @return 定位的字符，如果字符未找到返回0
	 * @throws JSONException IO异常
	 */
	public char skipTo(final char to) throws JSONException {
		char c;
		try {
			final long startIndex = this.index;
			final long startCharacter = this.character;
			final long startLine = this.line;
			mark(1000000);
			do {
				c = this.next();
				if (c == 0) {
					reset();
					this.index = startIndex;
					this.character = startCharacter;
					this.line = startLine;
					return c;
				}
			} while (c != to);
		} catch (final IOException e) {
			throw new JSONException(e);
		}
		this.back();
		return c;
	}

	/**
	 * Make a JSONException to signal a syntax error. <br>
	 * 构建 JSONException 用于表示语法错误
	 *
	 * @param message 错误消息
	 * @return A JSONException object, suitable for throwing
	 */
	public JSONException syntaxError(final String message) {
		return new JSONException(message + this);
	}

	/**
	 * Make a printable string of this JSONTokener.
	 *
	 * @return " at {index} [character {character} line {line}]"
	 */
	@Override
	public String toString() {
		return " at " + this.index + " [character " + this.character + " line " + this.line + "]";
	}
}