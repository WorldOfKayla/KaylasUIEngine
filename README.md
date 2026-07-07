# KaylasUI Engine

Версия: `1.17.0-AURELIA`  
Кодовое имя: `AURELIA`

### Как использовать?

Для начала создаем класс и наследуемся от `org.foxesworld.engine.Engine`.
После наследования конструктор вашего класса должен вызывать супер-класс со следующими аргументами:

```java
super(poolSize, worker, configFiles);
```

#### Аргументы

| Аргумент    | Тип                     | Описание                                         |
| ----------- | ----------------------- | ------------------------------------------------ |
| poolSize    | int                     | Размер пула потоков для многопоточной работы     |
| worker      | String                  | Имя рабочего                                     |
| configFiles | Map\<String, Class\<?>> | Коллекция с именами и POJO классами конфигурации |

#### Методы, которые нужно реализовать

| Метод                                                                                         | Описание                                               |
| --------------------------------------------------------------------------------------------- | ------------------------------------------------------ |
| preInit()                                                                                     | Предварительная инициализация                          |
| init()                                                                                        | Инициализация                                          |
| postInit()                                                                                    | Постинициализация                                      |
| onPanelsBuilt()                                                                               | Вызывается при построении всех панелей                 |
| onAdditionalPanelBuild(JPanel panel)                                                          | Вызывается при построении каждой дополнительной панели |
| onGuiBuilt()                                                                                  | Вызывается при завершении построения UI                |
| onPanelBuild(Map\<String, OptionGroups> panels, String componentGroup, Container parentPanel) | Вызывается при построении каждой панели                |
| actionPerformed(ActionEvent e)                                                                | Вызывается при совершении действия                     |
| updateFocus(boolean hasFocus)                                                                 | Вызывается при получении фокуса окном (или его потере) |

### Базовая реализация
Для построения UI нужен метод, он принимает массив стилей компонентов:
```java
    private void buildGui(String[] styles) {
        setStyleProvider(new StyleProvider(styles));
        setGuiBuilder(new GuiBuilder(this));
        getGuiBuilder().getComponentFactory().setComponentFactoryListener(new InitialValue(this)); //Регистрация слушателя, который вызывается при создании каждого компонента для установки базового значения
        getGuiBuilder().addGuiBuilderListener(this);
        getGuiBuilder().buildGuiAsync(fileProperties.getFrameTpl(), getFrame().getRootPanel());
        this.setIconUtils(new IconUtils(this));
    }
```
Пример базовой реализации есть в файле `org.foxesworld.engine.Test`
