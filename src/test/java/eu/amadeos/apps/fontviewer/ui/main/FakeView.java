package eu.amadeos.apps.fontviewer.ui.main;

import eu.amadeos.apps.fontviewer.domain.Coverage;
import eu.amadeos.apps.fontviewer.domain.FontFamily;
import eu.amadeos.apps.fontviewer.domain.SampleText;
import eu.amadeos.apps.fontviewer.ui.parts.Detail;

import java.awt.Font;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Records what the presenter shows and answers its questions from fields. */
final class FakeView implements MainView {
    List<FontFamily> families = List.of();
    Optional<FontFamily> selected = Optional.empty();
    FamilyModel family;
    SampleText sample;
    String sampleText;
    Coverage coverage;
    List<Detail> details = List.of();
    int page = -1;
    String status = "";
    final List<String> errors = new ArrayList<>();
    final List<String> infos = new ArrayList<>();
    final List<String> confirmations = new ArrayList<>();
    Optional<InstallTarget> target = Optional.of(InstallTarget.FOR_ME);
    boolean confirmAnswer = true;
    List<Path> chosen = List.of();
    List<String> compared;
    boolean about;
    boolean closed;
    boolean noFamily;
    int loading;
    int refreshes;

    @Override
    public void showLoading() {
        loading++;
    }

    @Override
    public void showFamilies(List<FontFamily> list, Optional<FontFamily> chosen) {
        families = list;
        selected = chosen;
    }

    @Override
    public void refreshList() {
        refreshes++;
    }

    @Override
    public void showFamily(FamilyModel model) {
        family = model;
        noFamily = false;
    }

    @Override
    public void showNoFamily() {
        noFamily = true;
        family = null;
    }

    @Override
    public void showSample(SampleText kind, String text) {
        sample = kind;
        sampleText = text;
    }

    @Override
    public void showInspection(String name, Optional<Font> font, Coverage newCoverage, List<Detail> rows) {
        coverage = newCoverage;
        details = rows;
    }

    @Override
    public void showPage(int newPage) {
        page = newPage;
    }

    @Override
    public void showStatus(String text) {
        status = text;
    }

    @Override
    public void showError(String message) {
        errors.add(message);
    }

    @Override
    public void showInfo(String message) {
        infos.add(message);
    }

    @Override
    public Optional<InstallTarget> askInstallTarget(List<String> fileNames) {
        return target;
    }

    @Override
    public boolean confirm(String message, String yesLabel) {
        confirmations.add(message);
        return confirmAnswer;
    }

    @Override
    public List<Path> chooseFontFiles(boolean multiple) {
        return chosen;
    }

    @Override
    public void openCompare(List<FontFamily> all, List<String> names) {
        compared = names;
    }

    @Override
    public void showAbout() {
        about = true;
    }

    @Override
    public void close() {
        closed = true;
    }

    String detail(String label) {
        return details.stream().filter(d -> d.label().equals(label)).map(Detail::value).findFirst().orElse(null);
    }
}
