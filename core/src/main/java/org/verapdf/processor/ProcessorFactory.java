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
package org.verapdf.processor;

import org.verapdf.component.Components;
import org.verapdf.core.VeraPDFException;
import org.verapdf.core.XmlSerialiser;
import org.verapdf.features.FeatureExtractorConfig;
import org.verapdf.metadata.fixer.MetadataFixerConfig;
import org.verapdf.pdfa.validation.profiles.ValidationProfile;
import org.verapdf.pdfa.validation.validators.ValidatorConfig;
import org.verapdf.processor.plugins.PluginsCollectionConfig;
import org.verapdf.processor.reports.BatchSummary;
import org.verapdf.processor.reports.Reports;

import javax.xml.bind.JAXBException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.EnumSet;

/**
 * @author <a href="mailto:carl@openpreservation.org">Carl Wilson</a>
 *         <a href="https://github.com/carlwilson">carlwilson AT github</a>
 * @version 0.1 Created 30 Oct 2016:22:18:23
 */

public final class ProcessorFactory {
	private ProcessorFactory() {

	}

	public static ProcessorConfig defaultConfig() {
		return ProcessorConfigImpl.defaultInstance();
	}

	public static ProcessorConfig fromValues(final ValidatorConfig config, final FeatureExtractorConfig featureConfig,
											 final PluginsCollectionConfig pluginsCollectionConfig,
			final MetadataFixerConfig fixerConfig, final EnumSet<TaskType> tasks) {
		return ProcessorConfigImpl.fromValues(config, featureConfig, pluginsCollectionConfig, fixerConfig, tasks);
	}

	public static ProcessorConfig fromValues(final ValidatorConfig config, final FeatureExtractorConfig featureConfig,
											 final PluginsCollectionConfig pluginsCollectionConfig,
			final MetadataFixerConfig fixerConfig, final EnumSet<TaskType> tasks, final String mdFolder) {
		return ProcessorConfigImpl.fromValues(config, featureConfig, pluginsCollectionConfig, fixerConfig, tasks, mdFolder);
	}

	public static ProcessorConfig fromValues(final ValidatorConfig config, final FeatureExtractorConfig featureConfig,
											 final PluginsCollectionConfig pluginsCollectionConfig,
			final MetadataFixerConfig fixerConfig, final EnumSet<TaskType> tasks, ValidationProfile customProfile) {
		return ProcessorConfigImpl.fromValues(config, featureConfig, pluginsCollectionConfig, fixerConfig, tasks, customProfile);
	}

	public static ProcessorConfig fromValues(final ValidatorConfig config, final FeatureExtractorConfig featureConfig,
											 final PluginsCollectionConfig pluginsCollectionConfig,
			final MetadataFixerConfig fixerConfig, final EnumSet<TaskType> tasks, ValidationProfile customProfile, final String mdFolder) {
		return ProcessorConfigImpl.fromValues(config, featureConfig, pluginsCollectionConfig, fixerConfig, tasks, customProfile, mdFolder);
	}

	public static void configToXml(final ProcessorConfig toConvert, final OutputStream stream, boolean format)
			throws JAXBException {
		XmlSerialiser.toXml(toConvert, stream, format, false);
	}

	public static ProcessorConfig configFromXml(final InputStream source) throws JAXBException {
		return XmlSerialiser.typeFromXml(ProcessorConfigImpl.class, source);
	}

	public static final ItemProcessor createProcessor(final ProcessorConfig config) {
		return ProcessorImpl.newProcessor(config);
	}

	public static final BatchProcessor fileBatchProcessor(final ProcessorConfig config) {
		return new BatchFileProcessor(createProcessor(config));
	}

	public static final BatchProcessingHandler rawResultHandler() throws VeraPDFException {
		return rawResultHandler(new PrintWriter(System.out));
	}

	public static final BatchProcessingHandler rawResultHandler(final Writer dest) throws VeraPDFException {
		return RawResultHandler.newInstance(dest);
	}

	public static final BatchProcessingHandler getHandler(FormatOption option, boolean isVerbose, int maxFailedChecksPerRule, boolean logPassed)
			throws VeraPDFException {
		return getHandler(option, isVerbose, System.out, maxFailedChecksPerRule, logPassed);
	}

	public static final BatchProcessingHandler getHandler(FormatOption option, boolean isVerbose,
			OutputStream reportStream, int maxFailedChecksPerRule, boolean logPassed) throws VeraPDFException {
		if (option == null)
			throw new IllegalArgumentException("Arg option can not be null"); //$NON-NLS-1$
		if (reportStream == null)
			throw new IllegalArgumentException("Arg reportStream can not be null"); //$NON-NLS-1$

		switch (option) {
		case TEXT:
			return SingleLineResultHandler.newInstance(reportStream, isVerbose);
		case XML:
			return rawResultHandler(new PrintWriter(reportStream));
		case MRR:
			return MrrHandler.newInstance(new PrintWriter(reportStream), logPassed, maxFailedChecksPerRule);
		default: // should not be reached
			throw new VeraPDFException("Unknown report format option: " + option); //$NON-NLS-1$
		}
	}

	public static void resultToXml(final ProcessorResult toConvert, final OutputStream stream, boolean prettyXml)
			throws JAXBException {
		XmlSerialiser.toXml(toConvert, stream, prettyXml, false);
	}

	public static ProcessorResult resultFromXml(final InputStream source) throws JAXBException {
		return XmlSerialiser.typeFromXml(ProcessorResultImpl.class, source);
	}

	public static void taskResultToXml(final TaskResult toConvert, final OutputStream dest, boolean prettyXml)
			throws JAXBException {
		XmlSerialiser.toXml(toConvert, dest, prettyXml, true);
	}

	public static TaskResult taskResultfromXml(final InputStream source) throws JAXBException {
		return XmlSerialiser.typeFromXml(TaskResultImpl.class, source);
	}

	public static final class BatchSummariser {
		private int jobs = 0;
		private int failedJobs = 0;
		private int valid = 0;
		private int invalid = 0;
		private int validExcep = 0;
		private int features = 0;
		private Components.Timer timer = Components.Timer.start();

		public void addProcessingResult(ProcessorResult result) {
			this.jobs++;
			if (!result.isValidPdf() || result.isEncryptedPdf()) {
				this.failedJobs++;
				return;
			}
			if (result.getTaskTypes().contains(TaskType.VALIDATE) && result.getResultForTask(TaskType.VALIDATE).isExecuted()) {
				if (!result.getResultForTask(TaskType.VALIDATE).isSuccess())
					this.validExcep++;
				if (result.getValidationResult().isCompliant())
					this.valid++;
				else
					this.invalid++;
			}
			if (result.getTaskTypes().contains(TaskType.EXTRACT_FEATURES) && result.getResultForTask(TaskType.EXTRACT_FEATURES).isExecuted()
					&& result.getResultForTask(TaskType.EXTRACT_FEATURES).isSuccess()) {
				this.features++;
			}
		}

		public BatchSummary summarise() {
			return Reports.createBatchSummary(this.timer, this.jobs, this.failedJobs, this.valid, this.invalid,
					this.validExcep, this.features);
		}
	}
}
