package net.villagerzock.erdplugin.ui.builder;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class BuiltDialog extends DialogWrapper {
    private JPanel panel;
    private List<DialogPartInstance<?>> parts;

    private static final DialogPartRegistry PART_REGISTRY = new DialogPartRegistry();
    private BuiltDialog(@Nullable Project project, List<DialogPartInstance<?>> parts) {
        super(project, false);

        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));
        this.parts = new ArrayList<>();
        for (DialogPartInstance<?> part : parts){
            panel.add(new JLabel(part.name));
            panel.add(part.get().getComponent());
            this.parts.add(part);
        }
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return panel;
    }

    public <T> T getValue(String value, Class<T> type){
        for (DialogPartInstance<?> part : parts){
            if (part.getName().equals(value)){
                return type.cast(part.get().getValue());
            }
        }
        return null;
    }
    public static Builder builder(Project project){
        return new Builder(project);
    }

    static {
        registerDialogPart(String.class,StringDialogPart::new);
        registerDialogPart(Boolean.class,BooleanDialogPart::new);
        registerDialogPart(Path.class, PathDialogType::new);
        registerDialogPart(Integer.class,IntegerDialogPart::new);
        registerDialogPart(int.class,IntegerDialogPart::new);
    }
    public static <T> void registerDialogPart(
            Class<T> type,
            Function<? super T, ? extends DialogPart<T>> constructor
    ) {
        PART_REGISTRY.securePut(type, (def, t)->constructor.apply(def));
    }

    public static <T> void registerDialogPart(
            Class<T> type,
            DialogPartConstructor<? super T> constructor
    ) {
        PART_REGISTRY.securePut(type,constructor);
    }

    public interface DialogPart<T>{
        Class<T> getType();
        T getValue();
        JComponent getComponent();
    }

    private static final class DialogPartRegistry extends HashMap<Class<?>, DialogPartConstructor<?>> {
        public <T> void securePut(Class<T> type, DialogPartConstructor<? super T> function) {
            put(type, function);
        }

        @SuppressWarnings("unchecked")
        public <T> DialogPartConstructor<T> secureGet(Class<T> type) {
            return (DialogPartConstructor<T>) get(type);
        }
    }
    public interface DialogPartConstructor<T>{
        DialogPart<T> apply(T defaultValue, Class<T> type);
    }
    private static class DialogPartInstance<T>{
        private final DialogPartConstructor<T> part;
        private final String name;
        private DialogPart<T> cache;
        private final T defaultValue;
        private final Class<T> type;

        private DialogPartInstance(DialogPartConstructor<T> part, String name, T defaultValue, Class<T> type) {
            this.part = part;
            this.name = name;
            this.defaultValue = defaultValue;
            this.type = type;
        }

        private DialogPart<T> get(){
            if (cache != null){
                return cache;
            }

            DialogPart<T> p = part.apply(defaultValue, type);
            cache = p;
            return p;
        }
        private String getName(){
            return name;
        }
    }

    public static class Builder{
        private final List<DialogPartInstance<?>> parts = new ArrayList<>();
        private final Project project;
        private Builder(Project project){
            this.project = project;
        }

        public <T> Builder addInput(String name, Class<T> type) {
            return addInput(name,type,null);
        }

        public <T> Builder addInput(String name, DialogPartConstructor<T> constructor, @NotNull T defaultValue){
            parts.add(new DialogPartInstance<>(constructor, name, defaultValue,(Class<T>) defaultValue.getClass()));
            return this;
        }

        public <T> Builder addInput(String name, Class<T> type, T defaultValue){
            DialogPartConstructor<T> constructor = type.isEnum() ? EnumDialogPart::new : PART_REGISTRY.secureGet(type);
            if (constructor == null) {
                throw new IllegalArgumentException("No DialogPart registered for type: " + type.getName());
            }

            parts.add(new DialogPartInstance<>(constructor, name, defaultValue,type));
            return this;
        }

        public BuiltDialog build(){
            BuiltDialog dialog = new BuiltDialog(project,parts);
            dialog.showAndGet();

            return dialog;
        }
    }

    // All Dialog Types

    private static class StringDialogPart implements DialogPart<String>{
        private JBTextField textField;

        public StringDialogPart(String def){
            if (def == null){
                def = "";
            }

            textField = new JBTextField(def);
        }
        @Override
        public Class<String> getType() {
            return String.class;
        }

        @Override
        public String getValue() {
            return textField.getText();
        }

        @Override
        public JComponent getComponent() {
            return textField;
        }
    }

    private static class EnumDialogPart<T> implements DialogPart<T>{
        private final Class<T> type;
        private final ComboBox<T> box;
        public EnumDialogPart(T def,Class<T> type){
            this.type = type;
            if (def == null && type.getEnumConstants().length != 0){
                def = type.getEnumConstants()[0];
            }
            this.box = new ComboBox<>(type.getEnumConstants());
            this.box.setItem(def);
        }
        @Override
        public Class<T> getType() {
            return type;
        }

        @Override
        public T getValue() {
            return box.getItem();
        }

        @Override
        public JComponent getComponent() {
            return box;
        }
    }
    private static class BooleanDialogPart implements DialogPart<Boolean>{
        private final JBCheckBox checkBox;

        public BooleanDialogPart(boolean defaultValue){
            this.checkBox = new JBCheckBox("",defaultValue);
        }
        @Override
        public Class<Boolean> getType() {
            return Boolean.class;
        }

        @Override
        public Boolean getValue() {
            return checkBox.isSelected();
        }

        @Override
        public JComponent getComponent() {
            return checkBox;
        }
    }

    private static class PathDialogType implements DialogPart<Path>{
        private final TextFieldWithBrowseButton location;

        private PathDialogType(Path def) {
            this.location = SaveLocationField.create(null,"Save as...", "Save file", "");
            String p;
            if (def == null){
                p = "";
            }else {
                p = def.toString();
            }
            location.setText(p);
        }

        @Override
        public Class<Path> getType() {
            return Path.class;
        }

        @Override
        public Path getValue() {
            return Paths.get(location.getText());
        }

        @Override
        public JComponent getComponent() {
            return location;
        }
    }

    private static final class SaveLocationField {

        public static TextFieldWithBrowseButton create(Project project, String title, String description,
                                                       String defaultFileName, String... extensions) {
            TextFieldWithBrowseButton field = new TextFieldWithBrowseButton();

            // Optional: Start-Ordner aus dem aktuellen Text bestimmen
            field.getButton().addActionListener(e -> {
                FileSaverDescriptor descriptor = new FileSaverDescriptor(title, description, extensions);

                FileSaverDialog dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project);

                VirtualFile baseDir = guessBaseDir(field.getText());
                var result = dialog.save(baseDir, defaultFileName); // null wenn abgebrochen
                if (result == null) return;

                File file = result.getFile();
                if (file == null) return;

                field.setText(file.getAbsolutePath());
            });

            return field;
        }

        private static VirtualFile guessBaseDir(String currentText) {
            if (currentText == null || currentText.isBlank()) return null;

            File f = new File(currentText);
            File dir = f.isDirectory() ? f : f.getParentFile();
            if (dir == null) return null;

            return LocalFileSystem.getInstance().findFileByIoFile(dir);
        }
    }

    private static class IntegerDialogPart implements DialogPart<Integer>{
        private final JBIntSpinner box;

        private IntegerDialogPart(Integer def) {
            if (def == null)
                def = 0;
            this.box = new JBIntSpinner(def,0,Integer.MAX_VALUE,1);
        }


        @Override
        public Class<Integer> getType() {
            return Integer.class;
        }

        @Override
        public Integer getValue() {
            return box.getNumber();
        }

        @Override
        public JComponent getComponent() {
            return box;
        }
    }
}
