package eu.amadeos.apps.fontviewer.ui.main;

import eu.amadeos.apps.fontviewer.OnEdt;
import eu.amadeos.apps.fontviewer.domain.FontFamily;
import eu.amadeos.apps.fontviewer.ui.parts.Detail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The real window with the presenter on test data. Needs a display. */
class MainWindowTest {
    @TempDir
    Path dir;

    private MainWindow window;
    private Fixture fixture;

    private void open() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        fixture = new Fixture(dir);
        OnEdt.run(() -> {
            window = new MainWindow(fixture.msg, null, fixture.loader, fixture.prefs);
            MainPresenter presenter = fixture.presenter(window);
            window.bind(presenter);
            window.setVisible(true);
            presenter.start(Optional.empty());
        });
    }

    @AfterEach
    void close() throws Exception {
        if (window != null) OnEdt.run(() -> window.close());
    }

    @Test
    void showsTheListAndTheChosenFamily() throws Exception {
        open();
        OnEdt.run(() -> {
            assertEquals(3, window.familyList().getModel().getSize());
            assertEquals("Caladea", window.familyList().getSelectedValue().name());
            assertEquals("Caladea", window.familyName());
            assertEquals("Caladea", window.familyName());
            assertEquals("Serif · 1 style · Installed by you", window.subtitleText());
            assertTrue(window.removeButton().isEnabled());
            assertEquals("3 font families, 1 of them installed by you", window.statusText());
            assertEquals("Caladea", window.waterfall().shownFont().getFamily());
            assertEquals(3, window.tabs().getTabCount());
            assertTrue(window.detailsPanel().details().stream().map(Detail::label).toList().contains("Licence"));
            assertEquals(96, window.characters().grid().count());
            BufferedImage img = new BufferedImage(window.getWidth(), window.getHeight(), BufferedImage.TYPE_INT_RGB);
            window.getContentPane().paint(img.getGraphics());
        });
    }

    @Test
    void choosingAnotherFamilyAndStyle() throws Exception {
        open();
        OnEdt.run(() -> {
            window.familyList().setSelectedIndex(2);
            assertEquals("Liberation Serif", window.familyName());
            assertFalse(window.removeButton().isEnabled());
            assertEquals("This font came with a program package. You remove it in Software.",
                    window.removeButton().getToolTipText());
            assertEquals(2, window.styleBox().getItemCount());
            window.styleBox().setSelectedItem("Bold");
            assertEquals("Bold", fixture.presenter.face().orElseThrow().style());
            assertFalse(window.menus().item("menu.font.remove").isEnabled());
        });
    }

    @Test
    void searchSampleAndPages() throws Exception {
        open();
        OnEdt.run(() -> {
            window.searchField().setText("mono");
            assertEquals(1, window.familyList().getModel().getSize());
            FontFamily only = window.familyList().getModel().getElementAt(0);
            assertEquals("DejaVu Sans Mono", only.name());
            window.searchField().setText("");
            window.sampleField().setText("Hallo Welt");
            assertEquals("Hallo Welt", window.waterfall().text());
            assertEquals("Hallo Welt", fixture.prefs.ownText());
            window.tabs().setSelectedIndex(2);
            assertEquals(2, fixture.prefs.page());
            window.menus().item("menu.view.characters").doClick();
            assertEquals(1, window.tabs().getSelectedIndex());
            assertNotNull(window.menus().item("menu.help.about"));
        });
    }

    @Test
    void compareWindow() throws Exception {
        open();
        OnEdt.run(() -> {
            fixture.presenter.addToCompare();
            assertNotNull(window.compareWindow());
            assertTrue(window.compareWindow().isVisible());
            assertEquals(java.util.List.of("Caladea"), window.compareWindow().comparedNames());
            window.familyList().setSelectedIndex(2);
            fixture.presenter.addToCompare();
            assertEquals(java.util.List.of("Liberation Serif", "Caladea"), window.compareWindow().comparedNames());
            window.compareWindow().sizeSlider().setValue(40);
            assertEquals(40f, window.compareWindow().rowFont(1).getSize2D());
            assertEquals("Caladea", window.compareWindow().rowFont(1).getFamily());
            assertEquals(40, fixture.prefs.compareSize());
            window.compareWindow().textField().setText("Test");
            window.compareWindow().removeAt(0);
            assertEquals(java.util.List.of("Caladea"), window.compareWindow().comparedNames());
            assertEquals(java.util.List.of("Caladea"), fixture.prefs.compared());
        });
    }
}
