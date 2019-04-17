/*
 * generated by Xtext 2.17.0
 */
package org.metafacture.fix.ide.contentassist.antlr;

import org.antlr.runtime.Token;
import org.antlr.runtime.TokenSource;
import org.eclipse.xtext.parser.antlr.AbstractIndentationTokenSource;
import org.metafacture.fix.ide.contentassist.antlr.internal.InternalFixParser;

public class FixTokenSource extends AbstractIndentationTokenSource {

	public FixTokenSource(TokenSource delegate) {
		super(delegate);
	}

	@Override
	protected boolean shouldSplitTokenImpl(Token token) {
		return token.getType() == InternalFixParser.RULE_WS;
	}

	@Override
	protected int getBeginTokenType() {
		return InternalFixParser.RULE_BEGIN;
	}

	@Override
	protected int getEndTokenType() {
		return InternalFixParser.RULE_END;
	}

	@Override
	protected boolean shouldEmitPendingEndTokens() {
		return false;
	}
}
