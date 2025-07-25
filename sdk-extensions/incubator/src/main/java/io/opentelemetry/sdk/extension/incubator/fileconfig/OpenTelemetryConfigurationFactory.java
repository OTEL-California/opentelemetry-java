/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.incubator.fileconfig;

import io.opentelemetry.api.incubator.config.DeclarativeConfigException;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Objects;
import java.util.regex.Pattern;

final class OpenTelemetryConfigurationFactory
    implements Factory<OpenTelemetryConfigurationModel, OpenTelemetrySdk> {

  private static final Pattern SUPPORTED_FILE_FORMATS = Pattern.compile("^(0.4)|(1.0(-rc.\\d*)?)$");

  private static final OpenTelemetryConfigurationFactory INSTANCE =
      new OpenTelemetryConfigurationFactory();

  private OpenTelemetryConfigurationFactory() {}

  static OpenTelemetryConfigurationFactory getInstance() {
    return INSTANCE;
  }

  @Override
  public OpenTelemetrySdk create(
      OpenTelemetryConfigurationModel model, DeclarativeConfigContext context) {
    OpenTelemetrySdkBuilder builder = OpenTelemetrySdk.builder();
    String fileFormat = model.getFileFormat();
    if (fileFormat == null || !SUPPORTED_FILE_FORMATS.matcher(fileFormat).matches()) {
      throw new DeclarativeConfigException(
          "Unsupported file format. Supported formats include 0.4, 1.0*");
    }
    // TODO(jack-berg): log warning if version is not exact match, which may result in unexpected
    // behavior for experimental properties.

    if (Objects.equals(Boolean.TRUE, model.getDisabled())) {
      return builder.build();
    }

    if (model.getPropagator() != null) {
      builder.setPropagators(
          PropagatorFactory.getInstance().create(model.getPropagator(), context));
    }

    Resource resource = Resource.getDefault();
    if (model.getResource() != null) {
      resource = ResourceFactory.getInstance().create(model.getResource(), context);
    }

    if (model.getLoggerProvider() != null) {
      builder.setLoggerProvider(
          context.addCloseable(
              LoggerProviderFactory.getInstance()
                  .create(
                      LoggerProviderAndAttributeLimits.create(
                          model.getAttributeLimits(), model.getLoggerProvider()),
                      context)
                  .setResource(resource)
                  .build()));
    }

    if (model.getTracerProvider() != null) {
      builder.setTracerProvider(
          context.addCloseable(
              TracerProviderFactory.getInstance()
                  .create(
                      TracerProviderAndAttributeLimits.create(
                          model.getAttributeLimits(), model.getTracerProvider()),
                      context)
                  .setResource(resource)
                  .build()));
    }

    if (model.getMeterProvider() != null) {
      builder.setMeterProvider(
          context.addCloseable(
              MeterProviderFactory.getInstance()
                  .create(model.getMeterProvider(), context)
                  .setResource(resource)
                  .build()));
    }

    return context.addCloseable(builder.build());
  }
}
