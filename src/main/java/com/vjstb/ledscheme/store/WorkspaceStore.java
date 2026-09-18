package com.vjstb.ledscheme.store;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vjstb.ledscheme.model.LibraryBundle;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.Workspace;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Локальное хранение рабочего пространства в JSON.
 * По умолчанию — файл в домашней папке пользователя (~/.led-scheme/workspace.json),
 * чтобы данные не зависели от каталога запуска.
 */
public class WorkspaceStore {

    private final ObjectMapper mapper;
    private final File workspaceFile;

    public WorkspaceStore() {
        this(defaultWorkspaceFile());
    }

    public WorkspaceStore(File workspaceFile) {
        this.workspaceFile = workspaceFile;
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public static File defaultWorkspaceFile() {
        String home = System.getProperty("user.home", ".");
        File dir = new File(home, ".led-scheme");
        return new File(dir, "workspace.json");
    }

    public File getWorkspaceFile() {
        return workspaceFile;
    }

    public Workspace load() {
        if (!workspaceFile.exists()) {
            return new Workspace();
        }
        if (workspaceFile.length() == 0) {
            // Баг-репорт 2026-09-15: до исправления save() ниже запись была НЕ атомарной
            // (mapper.writeValue прямо в workspaceFile), и обрыв процесса ровно во время
            // неё (зависшее на I/O приложение, снятое через диспетчер задач) оставлял этот
            // файл пустым -- на старте это падало необрабатываемым NPE внутри
            // migrateLegacyChains (root == MissingNode -> convertValue возвращает null).
            // Пустой файл — такой же однозначный сигнал "данных тут нет", как и
            // отсутствующий (ветка выше), а не повреждённый JSON с шансом на частичное
            // восстановление -- отвечаем на него так же, не обрывая запуск приложения.
            return new Workspace();
        }
        try {
            JsonNode root = mapper.readTree(workspaceFile);
            migrateLegacyNetworkManagerPlans(root);
            Workspace workspace = mapper.convertValue(root, Workspace.class);
            migrateLegacyChains(workspace);
            return workspace;
        } catch (IOException e) {
            throw new RuntimeException("Не удалось загрузить данные из " + workspaceFile + ": " + e.getMessage(), e);
        }
    }

    /** Одноразовая миграция: до Round 8 Сетевого менеджера {@code
     *  NetworkDevicePlacement}/{@code NetworkLink} физически лежали ВНУТРИ
     *  каждой {@code networks[i]} (поля "devices"/"links" узла сети), а
     *  адрес/маска/шлюз были полями самого устройства — одно физическое
     *  устройство, входящее в несколько сетей, приходилось заводить на поле
     *  ДВАЖДЫ. Теперь оба списка общие для всего {@code networkManagerPlan}
     *  (см. {@code model.NetworkManagerPlan} javadoc), а адрес переехал в
     *  {@code model.NetworkAttachment}.
     *
     * <p>Работает на СЫРОМ дереве JSON, ДО типизированной привязки к {@link
     * Workspace} — в отличие от {@link #migrateLegacyChains} (там поля
     * назначения всё ещё существуют в модели, просто на другом уровне
     * вложенности того же объекта, и миграция может пройти ПОСЛЕ обычной
     * десериализации). Здесь наоборот: {@code model.Network} больше не
     * объявляет полей "devices"/"links" вообще — обычная типизированная
     * десериализация в {@code Workspace.class} эти вложенные массивы просто
     * молча отбросила бы (в {@code ObjectMapper} выключен {@code
     * FAIL_ON_UNKNOWN_PROPERTIES}), и переносить было бы уже нечего. */
    private static void migrateLegacyNetworkManagerPlans(JsonNode root) {
        if (root == null || !root.isObject()) {
            return;
        }
        JsonNode projects = root.get("projects");
        if (projects == null || !projects.isArray()) {
            return;
        }
        for (JsonNode project : projects) {
            JsonNode scenes = project.get("scenes");
            if (scenes == null || !scenes.isArray()) {
                continue;
            }
            for (JsonNode scene : scenes) {
                JsonNode planNode = scene.get("networkManagerPlan");
                if (planNode instanceof ObjectNode plan) {
                    migrateOneNetworkManagerPlan(plan);
                }
            }
        }
    }

    private static void migrateOneNetworkManagerPlan(ObjectNode plan) {
        JsonNode networks = plan.get("networks");
        if (networks == null || !networks.isArray()) {
            return;
        }
        ArrayNode hoistedDevices = plan.has("devices") && plan.get("devices").isArray()
                ? (ArrayNode) plan.get("devices") : plan.putArray("devices");
        ArrayNode hoistedLinks = plan.has("links") && plan.get("links").isArray()
                ? (ArrayNode) plan.get("links") : plan.putArray("links");

        for (JsonNode networkNode : networks) {
            if (!(networkNode instanceof ObjectNode network)) {
                continue;
            }
            JsonNode legacyDevices = network.remove("devices");
            if (legacyDevices != null && legacyDevices.isArray()) {
                String networkId = network.path("id").asText(null);
                for (JsonNode deviceNode : legacyDevices) {
                    if (deviceNode instanceof ObjectNode device) {
                        attachLegacyAddress(device, networkId);
                        hoistedDevices.add(device);
                    }
                }
            }
            JsonNode legacyLinks = network.remove("links");
            if (legacyLinks != null && legacyLinks.isArray()) {
                hoistedLinks.addAll((ArrayNode) legacyLinks);
            }
        }
    }

    /** Заворачивает легаси ip/mask/gateway устройства в ОДНО {@code
     *  NetworkAttachment} на сеть, из которой оно мигрирует — сами ключи
     *  ipAddress/subnetMask/gateway на устройстве не трогает (типизированная
     *  привязка их всё равно свяжет с {@code @Deprecated}-полями {@code
     *  model.NetworkDevicePlacement}, существующими только ради обратной
     *  совместимости, см. её javadoc). */
    private static void attachLegacyAddress(ObjectNode device, String networkId) {
        if (device.has("attachments")) {
            return; // уже мигрировано -- не дублировать при повторном заходе
        }
        ObjectNode attachment = device.objectNode();
        if (networkId != null) {
            attachment.put("networkId", networkId);
        }
        attachment.put("ipAddress", device.path("ipAddress").asText(""));
        attachment.put("subnetMask", device.path("subnetMask").asText(""));
        attachment.put("gateway", device.path("gateway").asText(""));
        device.putArray("attachments").add(attachment);
    }

    /** Одноразовая миграция: в файлах проектов, сохранённых до Task #78, цепочки
     *  питания/сигнала хранились на уровне Screen, а не Scene. Переносим (не
     *  копируем) их наверх в список сцены и очищаем legacy-поля экрана — иначе они
     *  просто молча игнорировались бы всей текущей бизнес-логикой/отрисовкой,
     *  которая теперь читает только Scene.getPowerChains()/getSignalChains(). */
    private static void migrateLegacyChains(Workspace workspace) {
        for (Project project : workspace.getProjects()) {
            for (Scene scene : project.getScenes()) {
                for (Screen screen : scene.getScreens()) {
                    if (!screen.getPowerChains().isEmpty()) {
                        scene.getPowerChains().addAll(screen.getPowerChains());
                        screen.getPowerChains().clear();
                    }
                    if (!screen.getSignalChains().isEmpty()) {
                        scene.getSignalChains().addAll(screen.getSignalChains());
                        screen.getSignalChains().clear();
                    }
                }
            }
        }
    }

    /** Пишет во временный файл рядом и атомарно подменяет им {@code workspaceFile} —
     *  баг-репорт: {@code mapper.writeValue(workspaceFile, ...)} писал НАПРЯМУЮ в целевой
     *  файл, а это открывает его на запись (== мгновенно обнуляет содержимое) ЗАДОЛГО до
     *  того, как весь JSON окажется на диске; эта функция вызывается синхронно на каждое
     *  изменение модели (см. {@code AppModel#persist}), поэтому обрыв процесса ровно в
     *  этот момент (зависшее на I/O приложение, снятое пользователем через диспетчер
     *  задач) оставлял {@code workspace.json} ПУСТЫМ — весь рабочий стол пользователя
     *  оказался потерян именно так. Временный файл + {@link Files#move} с {@code
     *  ATOMIC_MOVE} гарантируют, что на диске в любой момент лежит либо полная старая
     *  версия, либо полная новая — никогда пустой/оборванный файл между ними. */
    public void save(Workspace workspace) {
        try {
            File dir = workspaceFile.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                throw new IOException("не удалось создать каталог " + dir);
            }
            File tmp = new File(dir, workspaceFile.getName() + ".tmp");
            mapper.writeValue(tmp, workspace);
            moveAtomicallyIfPossible(tmp, workspaceFile);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сохранить данные в " + workspaceFile + ": " + e.getMessage(), e);
        }
    }

    private static void moveAtomicallyIfPossible(File from, File to) throws IOException {
        try {
            java.nio.file.Files.move(from.toPath(), to.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            // Не все файловые системы (напр. некоторые сетевые шары) поддерживают
            // атомарную замену -- обычное переименование всё ещё безопаснее прямой
            // записи (файл подменяется одной операцией ОС, не байт-за-байтом).
            java.nio.file.Files.move(from.toPath(), to.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Экспорт библиотеки ОДНОГО типа (кабинеты/контроллеры/пресеты/кабели) в JSON-файл —
     *  оборачивается в {"kind": kind, "items": [...]}, чтобы импорт мог сам определить
     *  тип файла и не полагаться на ручной выбор пользователя (Task #5/v1.6: неверно
     *  выбранный в комбобоксе тип раньше молча создавал мусорные записи, т.к.
     *  FAIL_ON_UNKNOWN_PROPERTIES выключен и парсинг просто игнорировал лишние поля). */
    public <T> void exportList(List<T> items, File target, String kind) {
        try {
            java.util.Map<String, Object> wrapper = new java.util.LinkedHashMap<>();
            wrapper.put("kind", kind);
            wrapper.put("items", items);
            mapper.writeValue(target, wrapper);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось экспортировать библиотеку: " + e.getMessage(), e);
        }
    }

    /** Тип библиотеки, которым файл сам себя маркирует (см. {@link #exportList}) —
     *  null, если файл в старом формате (голый массив без обёртки) или маркер не
     *  распознан; тогда вызывающий код должен вернуться к ручному выбору типа,
     *  как раньше. */
    public String detectKind(File source) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(source);
            if (root != null && root.isObject() && root.has("kind") && root.has("items")) {
                return root.get("kind").asText(null);
            }
        } catch (IOException e) {
            // Не критично для автоопределения — просто не подскажем тип, ручной выбор остаётся как раньше.
        }
        return null;
    }

    /** Импорт библиотеки ОДНОГО типа из JSON-файла — понимает и старый формат
     *  (голый массив элементов), и новый (обёртка {"kind","items"} из {@link #exportList}). */
    public <T> List<T> importList(File source, Class<T> type) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(source);
            com.fasterxml.jackson.databind.JsonNode itemsNode = root.isObject() && root.has("items")
                    ? root.get("items") : root;
            return mapper.convertValue(itemsNode, mapper.getTypeFactory().constructCollectionType(List.class, type));
        } catch (IOException e) {
            throw new RuntimeException("Не удалось импортировать библиотеку: " + e.getMessage(), e);
        }
    }

    /** Экспорт ВСЕХ библиотек разом («Экспорт всего») в один JSON-файл. */
    public void exportBundle(LibraryBundle bundle, File target) {
        try {
            mapper.writeValue(target, bundle);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось экспортировать библиотеки: " + e.getMessage(), e);
        }
    }

    /** Импорт файла, ранее сохранённого через {@link #exportBundle}. */
    public LibraryBundle importBundle(File source) {
        try {
            return mapper.readValue(source, LibraryBundle.class);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось импортировать библиотеки: " + e.getMessage(), e);
        }
    }
}
