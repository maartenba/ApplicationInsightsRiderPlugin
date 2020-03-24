package fr.socolin.applicationinsights;

import com.jetbrains.rd.util.lifetime.LifetimeDefinition;
import com.jetbrains.rider.debugger.DotNetDebugProcess;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ApplicationInsightsSession {
    public final List<Telemetry> telemetries = new ArrayList<>();
    public List<Telemetry> filteredTelemetries = new ArrayList<>();
    private TelemetryFactory telemetryFactory;
    private DotNetDebugProcess dotNetDebugProcess;
    private Set<TelemetryType> enabledTelemetryTypes = new HashSet<>();
    @Nullable
    private Consumer<Telemetry> addedTelemetry;
    @Nullable
    private Consumer<List<Telemetry>> updateAllTelemetries;

    public ApplicationInsightsSession(
            TelemetryFactory telemetryFactory,
            DotNetDebugProcess dotNetDebugProcess
    ) {
        this.telemetryFactory = telemetryFactory;
        this.dotNetDebugProcess = dotNetDebugProcess;

        this.enabledTelemetryTypes.add(TelemetryType.Message);
        this.enabledTelemetryTypes.add(TelemetryType.Request);
        this.enabledTelemetryTypes.add(TelemetryType.Exception);
        this.enabledTelemetryTypes.add(TelemetryType.Event);
        this.enabledTelemetryTypes.add(TelemetryType.RemoteDependency);
    }

    public void startListeningToOutputDebugMessage() {
        dotNetDebugProcess.getSessionProxy().getTargetDebug().advise(new LifetimeDefinition(), outputMessageWithSubject -> {
            Telemetry telemetry = telemetryFactory.tryCreateFromDebugOutputLog(outputMessageWithSubject.getOutput());
            if (telemetry != null) {
                addTelemetry(telemetry);
            }
            return null;
        });
    }

    public void setTelemetryFiltered(TelemetryType telemetryType, boolean filtered) {
        if (filtered) {
            this.enabledTelemetryTypes.remove(telemetryType);
        } else {
            this.enabledTelemetryTypes.add(telemetryType);
        }
        updateFilteredTelemetries();
    }

    private void addTelemetry(Telemetry telemetry) {
        boolean visible = false;
        synchronized (telemetries) {
            telemetries.add(telemetry);
            if (enabledTelemetryTypes.contains(telemetry.getType())) {
                filteredTelemetries.add(telemetry);
                visible = true;
            }
        }
        if (visible && addedTelemetry != null)
            addedTelemetry.accept(telemetry);
    }

    private void updateFilteredTelemetries() {
        synchronized (telemetries) {
            filteredTelemetries = telemetries.stream()
                    .filter(e -> enabledTelemetryTypes.contains(e.getType()))
                    .collect(Collectors.toList());
        }
        if (updateAllTelemetries != null)
            updateAllTelemetries.accept(filteredTelemetries);
    }

    public boolean isEnabled(TelemetryType telemetryType) {
        return this.enabledTelemetryTypes.contains(telemetryType);
    }

    public void registerChanges(Consumer<Telemetry> addedTelemetry, Consumer<List<Telemetry>> updateAllTelemetries) {
        this.addedTelemetry = addedTelemetry;
        this.updateAllTelemetries = updateAllTelemetries;
    }

    public List<Telemetry> getFilteredTelemetries() {
        return Collections.unmodifiableList(this.filteredTelemetries);
    }
}