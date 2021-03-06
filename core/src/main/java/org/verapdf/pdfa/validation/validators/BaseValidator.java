/**
 * This file is part of veraPDF Library core, a module of the veraPDF project.
 * Copyright (c) 2015, veraPDF Consortium <info@verapdf.org>
 * All rights reserved.
 *
 * veraPDF Library core is free software: you can redistribute it and/or modify
 * it under the terms of either:
 *
 * The GNU General public license GPLv3+.
 * You should have received a copy of the GNU General Public License
 * along with veraPDF Library core as the LICENSE.GPL file in the root of the source
 * tree.  If not, see http://www.gnu.org/licenses/ or
 * https://www.gnu.org/licenses/gpl-3.0.en.html.
 *
 * The Mozilla Public License MPLv2+.
 * You should have received a copy of the Mozilla Public License along with
 * veraPDF Library core as the LICENSE.MPL file in the root of the source tree.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
/**
 * 
 */
package org.verapdf.pdfa.validation.validators;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.ScriptableObject;
import org.verapdf.component.ComponentDetails;
import org.verapdf.component.Components;
import org.verapdf.core.ModelParsingException;
import org.verapdf.core.ValidationException;
import org.verapdf.model.baselayer.Object;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.results.Location;
import org.verapdf.pdfa.results.TestAssertion;
import org.verapdf.pdfa.results.TestAssertion.Status;
import org.verapdf.pdfa.results.ValidationResult;
import org.verapdf.pdfa.results.ValidationResults;
import org.verapdf.pdfa.validation.profiles.Rule;
import org.verapdf.pdfa.validation.profiles.RuleId;
import org.verapdf.pdfa.validation.profiles.ValidationProfile;
import org.verapdf.pdfa.validation.profiles.Variable;

/**
 * @author <a href="mailto:carl@openpreservation.org">Carl Wilson</a>
 */
class BaseValidator implements PDFAValidator {
	private static final URI componentId = URI.create("http://pdfa.verapdf.org/validators#default");
	private static final String componentName = "veraPDF PDF/A Validator";
	private static final ComponentDetails componentDetails = Components.libraryDetails(componentId, componentName);
	private static final int OPTIMIZATION_LEVEL = 9;
	private final ValidationProfile profile;
	private Context context;
	private ScriptableObject scope;

	private final Deque<Object> objectsStack = new ArrayDeque<>();
	private final Deque<String> objectsContext = new ArrayDeque<>();
	private final Deque<Set<String>> contextSet = new ArrayDeque<>();
	protected final Set<TestAssertion> results = new HashSet<>();
	protected int testCounter = 0;
	protected boolean abortProcessing = false;
	protected final boolean logPassedTests;
	protected boolean isCompliant = true;

	private Map<RuleId, Script> ruleScripts = new HashMap<>();
	private Map<String, Script> variableScripts = new HashMap<>();

	private Set<String> idSet = new HashSet<>();

	protected String rootType;

	protected BaseValidator(final ValidationProfile profile) {
		this(profile, false);
	}

	protected BaseValidator(final ValidationProfile profile, final boolean logPassedTests) {
		super();
		this.profile = profile;
		this.logPassedTests = logPassedTests;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.verapdf.pdfa.PDFAValidator#getProfile()
	 */
	@Override
	public ValidationProfile getProfile() {
		return this.profile;
	}

	@Override
	public ValidationResult validate(PDFAParser toValidate) throws ValidationException {
		try {
			return this.validate(toValidate.getRoot());
		} catch (RuntimeException e) {
			throw new ValidationException("Caught unexpected runtime exception during validation", e);
		} catch (ModelParsingException excep) {
			throw new ValidationException("Parsing problem trying to validate.", excep);
		}
	}

	@Override
	public ComponentDetails getDetails() {
		return componentDetails;
	}

	protected ValidationResult validate(Object root) throws ValidationException {
		initialise();
		this.rootType = root.getObjectType();
		this.objectsStack.push(root);
		this.objectsContext.push("root");

		Set<String> rootIDContext = new HashSet<>();

		if (root.getID() != null) {
			rootIDContext.add(root.getID());
			this.idSet.add(root.getID());
		}

		this.contextSet.push(rootIDContext);

		while (!this.objectsStack.isEmpty() && !this.abortProcessing) {
			checkNext();
		}

		Context.exit();

		ValidationResult res = ValidationResults.resultFromValues(this.profile.getPDFAFlavour(), this.profile.getDetails(), this.results,
				this.isCompliant, this.testCounter);
		return res;
	}

	protected void initialise() {
		this.context = Context.enter();
		this.context.setOptimizationLevel(OPTIMIZATION_LEVEL);
		this.scope = this.context.initStandardObjects();
		this.objectsStack.clear();
		this.objectsContext.clear();
		this.contextSet.clear();
		this.results.clear();
		this.idSet.clear();
		this.testCounter = 0;
		this.isCompliant = true;
		initializeAllVariables();
	}

	private void initializeAllVariables() {
		for (Variable var : this.profile.getVariables()) {
			if (var == null)
				continue;

			java.lang.Object res;
			res = this.context.evaluateString(this.scope, var.getDefaultValue(), null, 0, null);
			if (res instanceof NativeJavaObject) {
				res = ((NativeJavaObject) res).unwrap();
			}

			this.scope.put(var.getName(), this.scope, res);
		}
	}

	private boolean checkNext() throws ValidationException {

		Object checkObject = this.objectsStack.pop();
		String checkContext = this.objectsContext.pop();
		Set<String> checkIDContext = this.contextSet.pop();

		boolean res = checkAllRules(checkObject, checkContext);

		updateVariables(checkObject);

		addAllLinkedObjects(checkObject, checkContext, checkIDContext);

		return res;
	}

	private void updateVariables(Object object) {
		if (object != null) {
			updateVariableForObjectWithType(object, object.getObjectType());

			for (String parentName : object.getSuperTypes()) {
				updateVariableForObjectWithType(object, parentName);
			}
		}
	}

	private void updateVariableForObjectWithType(Object object, String objectType) {
		for (Variable var : this.profile.getVariablesByObject(objectType)) {

			if (var == null)
				continue;

			java.lang.Object variable = evalVariableResult(var, object);
			this.scope.put(var.getName(), this.scope, variable);
		}
	}

	private java.lang.Object evalVariableResult(Variable variable, Object object) {
		Script script;
		if (!this.variableScripts.containsKey(variable.getName())) {
			String source = getStringScript(object, variable.getValue());
			script = this.context.compileString(source, null, 0, null);
			this.variableScripts.put(variable.getName(), script);
		} else {
			script = this.variableScripts.get(variable.getName());
		}

		this.scope.put("obj", this.scope, object);

		java.lang.Object res = script.exec(this.context, this.scope);

		if (res instanceof NativeJavaObject) {
			res = ((NativeJavaObject) res).unwrap();
		}
		return res;
	}

	private void addAllLinkedObjects(Object checkObject, String checkContext, Set<String> checkIDContext)
			throws ValidationException {
		List<String> links = checkObject.getLinks();
		for (int j = links.size() - 1; j >= 0; --j) {
			String link = links.get(j);

			if (link == null) {
				throw new ValidationException("There is a null link name in an object. Context: " + checkContext);
			}
			List<? extends Object> objects = checkObject.getLinkedObjects(link);
			if (objects == null) {
				throw new ValidationException("There is a null link in an object. Context: " + checkContext);
			}

			for (int i = 0; i < objects.size(); ++i) {
				Object obj = objects.get(i);

				StringBuilder path = new StringBuilder(checkContext);
				path.append("/");
				path.append(link);
				path.append("[");
				path.append(i);
				path.append("]");

				if (obj == null) {
					throw new ValidationException("There is a null link in an object. Context of the link: " + path);
				}

				if (checkRequired(obj, checkIDContext)) {
					this.objectsStack.push(obj);

					Set<String> newCheckIDContext = new HashSet<>(checkIDContext);

					if (obj.getID() != null) {
						path.append("(");
						path.append(obj.getID());
						path.append(")");

						newCheckIDContext.add(obj.getID());
						this.idSet.add(obj.getID());
					}

					this.objectsContext.push(path.toString());
					this.contextSet.push(newCheckIDContext);
				}
			}
		}
	}

	private boolean checkRequired(Object obj, Set<String> checkIDContext) {

		if (obj.getID() == null) {
			return true;
		} else if (obj.isContextDependent().booleanValue()) {
			return !checkIDContext.contains(obj.getID());
		} else {
			return !this.idSet.contains(obj.getID());
		}
	}

	private boolean checkAllRules(Object checkObject, String checkContext) {
		boolean res = true;
		Set<Rule> roolsForObject = this.profile.getRulesByObject(checkObject.getObjectType());
		for (Rule rule : roolsForObject) {
			res &= checkObjWithRule(checkObject, checkContext, rule);
		}

		for (String checkType : checkObject.getSuperTypes()) {
			roolsForObject = this.profile.getRulesByObject(checkType);
			if (roolsForObject != null) {
				for (Rule rule : roolsForObject) {
					if (rule != null) {
						res &= checkObjWithRule(checkObject, checkContext, rule);
					}
				}
			}
		}

		return res;
	}

	private static String getScript(Object obj, Rule rule) {
		return getStringScript(obj, "(" + rule.getTest() + ")==true");
	}

	private static String getStringScript(Object obj, String arg) {
		return getScriptPrefix(obj, arg) + arg + getScriptSuffix();
	}

	private static String getScriptPrefix(Object obj, String test) {
		StringBuilder builder = new StringBuilder();
		String[] vars = test.split("\\W");

		for (String prop : obj.getProperties()) {
			if (contains(vars, prop)) {
				builder.append("var ");
				builder.append(prop);
				builder.append(" = obj.get");
				builder.append(prop);
				builder.append("();\n");
			}
		}

		for (String linkName : obj.getLinks()) {
			if (contains(vars, linkName + "_size")) {
				builder.append("var ");
				builder.append(linkName);
				builder.append("_size = obj.getLinkedObjects(\"");
				builder.append(linkName);
				builder.append("\").size();\n");
			}
		}

		builder.append("function test(){return ");

		return builder.toString();
	}

	private static boolean contains(String[] values, String prop) {
		for (String value : values) {
			if (value.equals(prop)) {
				return true;
			}
		}
		return false;
	}

	private static String getScriptSuffix() {
		return ";}\ntest();";
	}

	private boolean checkObjWithRule(Object obj, String cntxtForRule, Rule rule) {
		this.scope.put("obj", this.scope, obj);
		Script scr;
		if (!this.ruleScripts.containsKey(rule.getRuleId())) {
			scr = this.context.compileString(getScript(obj, rule), null, 0, null);
			this.ruleScripts.put(rule.getRuleId(), scr);
		} else {
			scr = this.ruleScripts.get(rule.getRuleId());
		}

		boolean testEvalResult = ((Boolean) scr.exec(this.context, this.scope)).booleanValue();

		this.processAssertionResult(testEvalResult, cntxtForRule, rule);

		return testEvalResult;
	}

	protected void processAssertionResult(final boolean assertionResult, final String locationContext,
			final Rule rule) {
		this.testCounter++;
		Location location = ValidationResults.locationFromValues(this.rootType, locationContext);
		TestAssertion assertion = ValidationResults.assertionFromValues(this.testCounter, rule.getRuleId(),
				assertionResult ? Status.PASSED : Status.FAILED, rule.getDescription(), location);
		if (this.isCompliant)
			this.isCompliant = assertionResult;
		if (!assertionResult || this.logPassedTests)
			this.results.add(assertion);
	}

	@Override
	public void close() {
		/**
		 * Empty
		 */
	}
}
