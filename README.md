# Tinkoff bonds scanner

Приложение для поиска, фильтрации и ранжирования облигаций, доступных в [Тинькофф инвестициях](https://www.tinkoff.ru/invest/).

Фильтрация поддерживает несколько параметров (цена, номинал, количество дней до следующего купона, ...), на основании которых
в дальнейшем считается собираетельная характеристика облигации `Score`, по которой и производится ранжирование.

**Предупреждение:** характеристика `Score` не является гарантией правильного выбора облигации, это просто подсказка о том, 
на что стоит обратить внимание в первую очередь. Мы стараемся сделать как можно объективнее и точнее.

### Использованные ресурсы

* [Tinkoff investment Java SDK](https://github.com/TinkoffCreditSystems/invest-openapi-java-sdk) - получение списка доступных облигаций в Тинькофф инвестициях.
* [MOEX](https://iss.moex.com/) - получение детальной информации об облигации (названия, даты, купоны, последняя цена, ...).

### Требования перед запуском приложения

* Установленная виртуальная Java машина (JVM) версии 17 и выше.
  Скачать: [OpenJDK 17](https://jdk.java.net/java-se-ri/17) или [Oracle](https://www.oracle.com/java/technologies/downloads/).
* Аккаунт в Тинькофф инвестициях и ключ для API запросов. [Инструкция](https://tinkoffcreditsystems.github.io/invest-openapi/auth/) как его получить.
* Стабильное Интернет-соединение.

### Первый запуск

1. Скачайте последнюю версию приложения в отдельную директорию. Быстрое скачивание Windows: [EXE](https://github.com/Shemplo/TBS/releases/latest/download/TBS.exe).
2. Полученный API токен поместите в файл `token.txt` в той же директории.
3. Запустите приложение (EXE файл) двойным кликом по нему. 
     
     Сначала откроется окно командной строки, затем пользовательский интерфейс (если не произойдёт фатальных ошибок). 
     При запуске в режиме сканирования облигаций (в том числе при первом запуске) процесс может занять некоторое время (до 2-3х минут). 

4. Если вы всё сделали правильно, то в появившемся окне приложения должна быть не пустая таблица с найденными облигациями.
* Для пользователей **не Windows** перед первым шагом придётся выполнить самостоятельную сборку приложения, 
  потому что в приложении есть платформа-зависимые компоненты:
    1. Склонируйте этот репозиторий к себе на компьютер.
    2. Установите систему сборки [Maven](https://maven.apache.org/) последней версии.
    3. В директории, куда склонировался репозиторий (в этой директории должен быть файл `pom.xml`) откройте командную строку и выполните команду `mvn package`.
    4. Собранное приложение будет находится в директории `target`, файл `TBS.jar`. Для того, чтобы увидеть сообщения в консоли рекомендуется запускать этот файл
       через командную строку `java -jar TBS.jar`

### Продвинутый запуск и конфигурация

По умолчанию приложение запустится с параметрами `DEFAULT_RUB`, заданные в файле [ProfilePreset.java](https://github.com/Shemplo/TBS/blob/master/src/main/java/ru/shemplo/tbs/entity/ProfilePreset.java). 
Если Вас это не устраивает, то приложение поддерживает гибкую кастомизацию параметров.

* Если вы хотите использовать готовый пресет из того же файла, в консольной строке запустите приложение с именем пресета
  `TBS.exe RISCKY_RUB` или `java -jar TBS.jar RISCKY_RUB`.
* Если вы хотите использовать полностью свой пресет:
    1. Создайте `.xml` файл с любым именем `{{NAME}}` в той же директории, где находится приложение.
    2. Скопируйте шаблон содержимого в созданный файл
        ```xml
        <?xml version="1.0" encoding="UTF-8"?>
        <profile>
          <name>Custom profile name</name>
          <token filename="{{TOKEN}}" responsible="1" />
          <general mr="30" inflation="0.065" />
          <params mte="24" cpy="4" mdtc="30" nv="1000.0" minp="6.0" maxpr="1000" />
          <currencies>RUB</currencies>
          <cmodes>FIXED, NOT_FIXED</cmodes>
          <bannede>-1L</bannede>
        </profile>
        ```
     3. Измените необходимые параметры на Ваши значения.
        
        В теге `token` замените `{{TOKEN}}` на абсолютный или относительный путь до файла с вашим API токеном.
        Если какой-то атрибут тега `params` не указан (все они необязательные), то соответствующий параметр не будет использоваться для фильтрации.
        Имена атрибутов получены из первых букв слов в названии соответствующего параметра, которые можно увидеть в файле [ProfilePreset.java](https://github.com/Shemplo/TBS/blob/master/src/main/java/ru/shemplo/tbs/entity/ProfilePreset.java).
        В теге `bannede` можно перечислить через запятую идентификаторы эмитентов, облигации которых точно не должны попасть в таблицу с результатами сканирования.
      
     4. Запустите приложение с Вашими параметрами `TBS.exe {{NAME}}.xml` или `java -jar TBS.jar {{NAME}}.xml`.

### Куда смотреть, что нажимать?

Приложение имеет довольно простой пользовательский интерфейс, и удобство использования должно прийти с опытом, но про некоторые моменты всё-таки стоит рассказать:

* Для того, чтобы открыть страницу облигаций на сайте Тинькофф инвестиций или Московской биржи (MOEX) можно нажать соответствующие кнопки 
  `🌐` в начале каждой строки в таблице.
* Чтобы просмотреть купоны по какой-то облигации необходимо нажать на кнопку `🔍`.
    * Первая колонка может содержать символ следующего купона (➥), либо символ оферты (⭿), 
      после которой величина купона может измениться как в большую, так и в меньшую сторону.
    * Далее идут колонки, которые содержат дату купона; величину купона; информацию о том, известна ли достоверна величина купона
      (если нет, то берётся величина предыдущего купона), а также доходность купона с учётом инфляции.
    * Если величина купона не известна достоверно (не опубликована ещё эмитентом), то берётся значение предыдущего купона, а при расчёте доходности применяется
      коэффициент `0.9` как штраф за недостоверную информацию.
* В колонке `📎` можно отметить облигации, которые будут использоваться в планировании (подробнее об этом можно прочитать на сайте приложения).
* Далее идут колонки название и тикер облигации; валюта; количество лотов данного тикера во всех Ваших счетах (`👝`); суммарный доход с учётом инфляции; 
  доход по купонам с учётом инфляции; последняя цена сделки; номинал; количество купонов в год; дата следующего купона; фиксированные или нефиксированные купоны; 
  количество лет и месяцев до выхода облигации из обращения и априорный процент доходности с Московской биржи.
    * Учёт инфляции выполнен следующим образом, пусть `S` - некоторая денежная сумма на сегодняшний день, пусть `D` - это количество дней, а `I` - уровень инфляции
      в процентах, тогда значение `s` равное `S / (1 + I)^(D / 365)` - будет называться соответствующей денежной суммой `S` через `D` дней с уровнем инфляции `I`.
* В планировщике есть неочевидный параметр диверсификации. Формально, он определяет угол кривой распределения значения, простыми словами это значит, 
  что при значении 0% всё значение величины будет назначено первой облигации в таблице, при 100% – распределяемая величина будет равномерно (почти)
  назначена всем облигациям.
* Цены в таблице не обновляются в режиме реального времени, поэтому фактическая цена на сайте Тинькофф инвестиций может отличаться 
  от приведённой в таблице после запуска приложения.
* Любое значение в таблицах можно скопировать в буфер обмена двойным щелчком по ячейке таблицы.

### В дальнейших планах

* Полностью перейти на пользовательский интерфейс для запуска, редактирования профилей и прочей функциональности
* Автоматическая сборка для всех платформ (в том числе сборка со встроенной Java машиной)
