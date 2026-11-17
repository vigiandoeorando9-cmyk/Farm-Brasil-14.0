package com.example.domain

private val RED_TATU = rgb(0.84, 0.36, 0.08)
private val RED_BALDAN = rgb(0.78, 0.15, 0.13)
private val BLUE_SAO_JOSE = rgb(0.12, 0.28, 0.62)
private val GREEN_TRITON = rgb(0.14, 0.44, 0.22)
private val RED_MEC_RUL = rgb(0.82, 0.22, 0.14)
private val GREEN_ANZABEL = rgb(0.24, 0.48, 0.20)
private val BLUE_IKEDA = rgb(0.14, 0.38, 0.68)
private val GREEN_JAN = rgb(0.18, 0.52, 0.22)
private val ORANGE_JACTO = rgb(0.90, 0.42, 0.08)
private val BLUE_NOGUEIRA = rgb(0.16, 0.44, 0.72)
private val SILVER = rgb(0.76, 0.78, 0.80)
private val DARK = rgb(0.18, 0.20, 0.22)
private val WHITE = rgb(0.92, 0.92, 0.90)
private val YELLOW_ROTOR = rgb(0.92, 0.72, 0.10)

private fun toImp(
    id: String,
    brand: String,
    model: String,
    category: ImplementCategory,
    hitchType: HitchType,
    requiredHp: Double,
    workingWidthM: Double,
    weightKg: Double,
    capacityL: Double,
    priceBrl: Double,
    colorRgb: RgbTriple,
    description: String,
    requiredModPackId: String? = null,
    subTypeLabel: String = "",
): ImplementDef = ImplementDef(
    id = id,
    brand = brand,
    model = model,
    category = category,
    hitchType = hitchType,
    requiredHp = requiredHp,
    workingWidthM = workingWidthM,
    weightKg = weightKg,
    capacityL = capacityL,
    priceBrl = priceBrl,
    colorRgb = colorRgb,
    description = description,
    requiredModPackId = requiredModPackId,
    subTypeLabel = subTypeLabel,
)

private val EXTRA_IMPLEMENTS_ROWS: List<ImplementDef> = listOf(
    // Arados (8)
    toImp("arado_baldan_3d", "Baldan", "Arado Fixo AF 3 Discos", ImplementCategory.ARADO, HitchType.THREE_POINT, 35.0, 1.8, 520.0, 0.0, 12800.0, RED_BALDAN, "Arado compacto para tratores pequenos em hortas e pequenas áreas.", null, "Arados"),
    toImp("arado_tatu_5d", "Tatu Marchesan", "Arado Reversível ARH 5 Discos", ImplementCategory.ARADO, HitchType.THREE_POINT, 80.0, 2.8, 980.0, 0.0, 26500.0, RED_TATU, "Reversível hidráulico para aração uniforme em curvas de nível.", null, "Arados"),
    toImp("arado_jan_aiveca_4", "Jan", "Arado de Aiveca 4 Corpos", ImplementCategory.ARADO, HitchType.THREE_POINT, 105.0, 2.6, 1180.0, 0.0, 31000.0, GREEN_JAN, "Inversão profunda de leiva para controle de plantas daninhas resistentes.", null, "Arados"),
    toImp("arado_kuhn_6_corpos", "Kuhn", "Arado Vari-Master 6 Corpos", ImplementCategory.ARADO, HitchType.DRAWBAR, 165.0, 3.6, 2250.0, 0.0, 74000.0, rgb(0.82, 0.16, 0.14), "Arado semi-montado de alta largura para grandes tratores.", null, "Arados"),
    toImp("arado_lemken_8", "Lemken", "Diamant 16 8 Corpos", ImplementCategory.ARADO, HitchType.DRAWBAR, 260.0, 4.8, 3600.0, 0.0, 132000.0, rgb(0.12, 0.42, 0.78), "Arado pesado articulado para preparo rápido de grandes talhões.", null, "Arados"),
    toImp("arado_santa_izabel_4", "Santa Izabel", "Arado Subsolo Aiveca 4L", ImplementCategory.ARADO, HitchType.THREE_POINT, 95.0, 2.5, 1040.0, 0.0, 28000.0, RED_BALDAN, "Construção reforçada nacional para solos argilosos e pesados.", null, "Arados"),
    toImp("arado_baldan_ar6", "Baldan", "Arado Reversível AR-6", ImplementCategory.ARADO, HitchType.THREE_POINT, 115.0, 3.2, 1340.0, 0.0, 38500.0, RED_BALDAN, "Maior largura operacional para preparo convencional em propriedades médias.", null, "Arados"),
    toImp("arado_civemasa_7d", "Civemasa", "Arado Pesado 7 Discos", ImplementCategory.ARADO, HitchType.DRAWBAR, 180.0, 4.2, 2850.0, 0.0, 69000.0, rgb(0.84, 0.18, 0.14), "Especial para áreas de abertura de cana-de-açúcar e pastagens antigas.", null, "Arados"),

    // Grades (10)
    toImp("grade_baldan_14d", "Baldan", "Grade Aradora GAI 14 Discos", ImplementCategory.GRADE, HitchType.DRAWBAR, 65.0, 2.2, 1150.0, 0.0, 22500.0, RED_BALDAN, "Grade leve para pequenos talhões e pomares.", null, "Grades"),
    toImp("grade_tatu_18d", "Tatu Marchesan", "Grade Aradora GAC 18 Discos", ImplementCategory.GRADE, HitchType.DRAWBAR, 85.0, 2.6, 1520.0, 0.0, 31500.0, RED_TATU, "Descompactação superficial e incorporação de corretivos.", null, "Grades"),
    toImp("grade_baldan_32d", "Baldan", "Grade Niveladora NVCR 32 Discos", ImplementCategory.GRADE, HitchType.DRAWBAR, 95.0, 3.8, 1780.0, 0.0, 39000.0, RED_BALDAN, "Acabamento fino do leito de semeadura antes da plantadeira.", null, "Grades"),
    toImp("grade_tatu_36d", "Tatu Marchesan", "Grade Aradora Intermediária 36D", ImplementCategory.GRADE, HitchType.DRAWBAR, 150.0, 4.4, 3100.0, 0.0, 68000.0, RED_TATU, "Controle de transporte por rodas hidráulicas para estradas.", null, "Grades"),
    toImp("grade_civemasa_20d", "Civemasa", "Super Grade Pesada 20x32\"", ImplementCategory.GRADE, HitchType.DRAWBAR, 210.0, 3.9, 4400.0, 0.0, 98000.0, rgb(0.84, 0.18, 0.14), "Discos gigantes de 32 polegadas para reforma de canavial e pasto degradado.", null, "Grades"),
    toImp("grade_piccin_48d", "Piccin", "Grade Niveladora GNCRP 48 Discos", ImplementCategory.GRADE, HitchType.DRAWBAR, 185.0, 5.8, 3850.0, 0.0, 89000.0, rgb(0.82, 0.48, 0.12), "Dobrável hidraulicamente para deslocamento fácil em carreadores.", null, "Grades"),
    toImp("grade_jan_60d", "Jan", "Mega Grade Niveladora 60D", ImplementCategory.GRADE, HitchType.DRAWBAR, 270.0, 7.2, 5400.0, 0.0, 145000.0, GREEN_JAN, "Alta produtividade diária no Cerrado para nivelamento pré-plantio.", null, "Grades"),
    toImp("grade_horsch_joker6", "Horsch", "Joker 6 RT Grade Curta", ImplementCategory.GRADE, HitchType.DRAWBAR, 190.0, 6.0, 4100.0, 0.0, 168000.0, rgb(0.76, 0.14, 0.14), "Discos independentes com rolo compactador traseiro em alta velocidade.", null, "Grades"),
    toImp("grade_amazone_catros8", "Amazone", "Catros 8003-2T 8.0m", ImplementCategory.GRADE, HitchType.DRAWBAR, 260.0, 8.0, 5800.0, 0.0, 218000.0, rgb(0.20, 0.48, 0.22), "Incorporação veloz de palhada de milho e cobertura vegetal.", null, "Grades"),
    toImp("grade_vaderstad_carrier9", "Vaderstad", "Carrier XL 925 9.2m", ImplementCategory.GRADE, HitchType.DRAWBAR, 340.0, 9.2, 7400.0, 0.0, 295000.0, rgb(0.82, 0.14, 0.14), "Para tratores articulados de alta potência em grandes áreas.", null, "Grades"),

    // Subsoladores e Escarificadores (10)
    toImp("subsolador_baldan_3h", "Baldan", "Subsolador AST 3 Hastes", ImplementCategory.SUBSOLADOR, HitchType.THREE_POINT, 75.0, 2.0, 790.0, 0.0, 19500.0, RED_BALDAN, "Rompimento de pé-de-grade para tratores médios.", null, "Subsoladores"),
    toImp("subsolador_stara_asa7", "Stara", "ASA Laser CR 7 Hastes", ImplementCategory.SUBSOLADOR, HitchType.DRAWBAR, 190.0, 4.2, 2950.0, 0.0, 78000.0, ORANGE_JACTO, "Subsolador articulado com disco de corte de palhada e rolo destorroador.", null, "Subsoladores"),
    toImp("subsolador_stara_asa11", "Stara", "ASA Laser 11 Hastes Heavy", ImplementCategory.SUBSOLADOR, HitchType.DRAWBAR, 310.0, 6.2, 4600.0, 0.0, 138000.0, ORANGE_JACTO, "Descompactação de precisão até 45 cm de profundidade em grandes talhões.", null, "Subsoladores"),
    toImp("subsolador_jan_9h", "Jan", "Subsolador Jumbo 9 Hastes", ImplementCategory.SUBSOLADOR, HitchType.DRAWBAR, 240.0, 5.0, 3550.0, 0.0, 94000.0, GREEN_JAN, "Chassi tubular reforçado com desarme automático por mola.", null, "Subsoladores"),
    toImp("subsolador_tatu_13h", "Tatu Marchesan", "AST Mega 13 Hastes", ImplementCategory.SUBSOLADOR, HitchType.DRAWBAR, 420.0, 7.6, 6200.0, 0.0, 198000.0, RED_TATU, "Desenvolvido para articulados 4WD e esteiras acima de 400 cv.", null, "Subsoladores"),
    toImp("escarificador_jan_5h", "Jan", "Escarificador Ripper 5 Hastes", ImplementCategory.ESCARIFICADOR, HitchType.THREE_POINT, 85.0, 2.6, 950.0, 0.0, 24000.0, GREEN_JAN, "Aera o solo mantendo até 70% da palhada na superfície.", null, "Escarificadores"),
    toImp("escarificador_piccin_7h", "Piccin", "Escarificador EPCR 7 Hastes", ImplementCategory.ESCARIFICADOR, HitchType.DRAWBAR, 130.0, 3.5, 1650.0, 0.0, 42000.0, rgb(0.84, 0.48, 0.12), "Controle mecânico de compactação superficial em plantio direto.", null, "Escarificadores"),
    toImp("escarificador_tatu_9h", "Tatu Marchesan", "Escarificador Hidro-Ripper 9H", ImplementCategory.ESCARIFICADOR, HitchType.DRAWBAR, 185.0, 4.4, 2450.0, 0.0, 62000.0, RED_TATU, "Rolo nivelador traseiro integrado que deixa o solo pronto para plantio.", null, "Escarificadores"),
    toImp("escarificador_stara_fox11", "Stara", "Fox 11 Hastes Escarificador", ImplementCategory.ESCARIFICADOR, HitchType.DRAWBAR, 245.0, 5.4, 3400.0, 0.0, 96000.0, ORANGE_JACTO, "Hastes parabólicas de baixa demanda de potência por metro.", null, "Escarificadores"),
    toImp("escarificador_horsch_tiger8", "Horsch", "Tiger 8 MT Misto", ImplementCategory.ESCARIFICADOR, HitchType.DRAWBAR, 380.0, 7.5, 7200.0, 0.0, 265000.0, rgb(0.76, 0.14, 0.14), "Combina discos cortadores, hastes escarificadoras e rolo compactador.", null, "Escarificadores"),

    // Plantadeiras e Semeadoras (14)
    toImp("plantadeira_jumil_5l", "Jumil", "Exacta Air 5 Linhas", ImplementCategory.PLANTADEIRA, HitchType.THREE_POINT, 60.0, 2.5, 1450.0, 850.0, 48000.0, rgb(0.85, 0.2, 0.16), "Ideal para milho e feijão em propriedades familiares.", null, "Plantadeiras"),
    toImp("plantadeira_tatu_7l", "Tatu Marchesan", "COP Suprema 7 Linhas", ImplementCategory.PLANTADEIRA, HitchType.DRAWBAR, 80.0, 3.5, 2750.0, 1400.0, 74000.0, RED_TATU, "Robusta com sulcador guilhotina para plantio direto.", null, "Plantadeiras"),
    toImp("plantadeira_stara_11l", "Stara", "Princesa 11 Linhas Articulada", ImplementCategory.PLANTADEIRA, HitchType.DRAWBAR, 120.0, 5.2, 4100.0, 2400.0, 148000.0, ORANGE_JACTO, "Articulada para transporte estreito em estradas rurais.", null, "Plantadeiras"),
    toImp("plantadeira_stara_18l", "Stara", "Absoluta 18 Linhas Pneumática", ImplementCategory.PLANTADEIRA, HitchType.DRAWBAR, 195.0, 8.4, 6800.0, 3900.0, 265000.0, ORANGE_JACTO, "Dosador pneumático com corte de linha e taxa variável.", null, "Plantadeiras"),
    toImp("plantadeira_jd_1113", "John Deere", "1113 Vacuum 13 Linhas", ImplementCategory.PLANTADEIRA, HitchType.DRAWBAR, 145.0, 6.0, 5100.0, 2800.0, 185000.0, C.JD_GREEN, "Alta uniformidade de emergência em soja e milho.", null, "Plantadeiras"),
    toImp("plantadeira_jd_db50", "John Deere", "DB50 ExactEmerge 32 Linhas", ImplementCategory.PLANTADEIRA, HitchType.DRAWBAR, 340.0, 15.0, 12200.0, 6400.0, 580000.0, C.JD_GREEN, "Plantio em alta velocidade com precisão milimétrica.", null, "Plantadeiras"),
    toImp("plantadeira_jd_db90", "John Deere", "DB90 Mega 36 Linhas", ImplementCategory.PLANTADEIRA, HitchType.DRAWBAR, 450.0, 18.0, 15400.0, 7800.0, 760000.0, C.JD_GREEN, "Para tratores articulados de alta potência em mega chapadões.", null, "Plantadeiras"),
    toImp("plantadeira_mf_momentum24", "Massey Ferguson", "Momentum 24 Linhas Dobrável", ImplementCategory.PLANTADEIRA, HitchType.DRAWBAR, 250.0, 11.2, 9400.0, 5200.0, 395000.0, C.MF_RED, "Chassis dobrável com distribuição de peso inteligente nas asas.", null, "Plantadeiras"),
    toImp("plantadeira_valtra_30l", "Valtra", "Momentum 30L Smart Frame", ImplementCategory.PLANTADEIRA, HitchType.DRAWBAR, 310.0, 13.8, 11200.0, 5800.0, 485000.0, C.VALTRA_GOLD, "Sistema vDrive elétrico linha a linha.", null, "Plantadeiras"),
    toImp("plantadeira_case_fast24", "Case IH", "Fast Riser 6124 24 Linhas", ImplementCategory.PLANTADEIRA, HitchType.DRAWBAR, 255.0, 11.0, 9200.0, 5000.0, 410000.0, C.CASE_RED, "Três seções pantográficas para copiar ondulações do terreno.", null, "Plantadeiras"),
    toImp("semeadora_semeato_19l", "Semeato", "SHM 19/21 Semeadora Fina", ImplementCategory.SEMEADORA, HitchType.DRAWBAR, 85.0, 3.6, 2600.0, 1800.0, 72000.0, rgb(0.16, 0.5, 0.22), "Referência no Sul para trigo, aveia, cevada e canola.", null, "Semeadoras"),
    toImp("semeadora_stara_guapa28", "Stara", "Guapa Supra Winter 28 Linhas", ImplementCategory.SEMEADORA, HitchType.DRAWBAR, 145.0, 5.2, 4200.0, 2900.0, 142000.0, ORANGE_JACTO, "Especialista em arroz irrigado e culturas de inverno.", null, "Semeadoras"),
    toImp("semeadora_horsch_pronto9", "Horsch", "Pronto 9 DC Fluxo Contínuo", ImplementCategory.SEMEADORA, HitchType.DRAWBAR, 260.0, 9.0, 7800.0, 5000.0, 315000.0, rgb(0.76, 0.14, 0.14), "Caixa pressurizada de 5.000L para semeadura de grãos finos em alta velocidade.", null, "Semeadoras"),
    toImp("semeadora_kuhn_espro6", "Kuhn", "Espro 6000R 6.0m", ImplementCategory.SEMEADORA, HitchType.DRAWBAR, 195.0, 6.0, 5400.0, 3500.0, 215000.0, rgb(0.82, 0.16, 0.14), "Baixa demanda de tração com discos de corte duplos.", null, "Semeadoras"),

    // Pulverizadores e Distribuidores (12)
    toImp("pulv_jacto_condor800", "Jacto", "Condor 800 AM18 3 Pontos", ImplementCategory.PULVERIZADOR, HitchType.THREE_POINT, 55.0, 6.0, 720.0, 800.0, 38000.0, ORANGE_JACTO, "Pulverizador acoplado leve para pequenas glebas e pomares.", null, "Pulverizadores"),
    toImp("pulv_jacto_columbia2000", "Jacto", "Columbia 2000 Cross", ImplementCategory.PULVERIZADOR, HitchType.DRAWBAR, 75.0, 7.2, 1850.0, 2000.0, 88000.0, ORANGE_JACTO, "Tanque de 2.000L com estabilização de barra de baixo impacto.", null, "Pulverizadores"),
    toImp("pulv_kuhn_metris4100", "Kuhn", "Metris 4102 36m", ImplementCategory.PULVERIZADOR, HitchType.DRAWBAR, 130.0, 11.5, 3400.0, 4100.0, 195000.0, rgb(0.82, 0.16, 0.14), "Barras de alumínio com suspensão pneumática e corte por seção.", null, "Pulverizadores"),
    toImp("pulv_amazone_ux5200", "Amazone", "UX 5201 Super 40m", ImplementCategory.PULVERIZADOR, HitchType.DRAWBAR, 160.0, 13.0, 4200.0, 5200.0, 268000.0, rgb(0.20, 0.48, 0.22), "Grande autonomia para fazendas extensas de soja e algodão.", null, "Pulverizadores"),
    toImp("pulv_horsch_leeb6", "Horsch", "Leeb 6 GS Inox 6.000L", ImplementCategory.PULVERIZADOR, HitchType.DRAWBAR, 190.0, 14.5, 4950.0, 6000.0, 345000.0, rgb(0.76, 0.14, 0.14), "Tanque de aço inoxidável e BoomControl para aplicação rente à cultura.", null, "Pulverizadores"),
    toImp("pulv_jacto_arbus2000", "Jacto", "Arbus 2000 Turbo Fruticultura", ImplementCategory.PULVERIZADOR, HitchType.DRAWBAR, 60.0, 5.0, 1280.0, 2000.0, 74000.0, ORANGE_JACTO, "Atomizador para cafezais, laranjais, maçã e parreirais.", null, "Pulverizadores"),
    toImp("dist_nogueira_600", "Nogueira", "Rotaflow 600 Pendular", ImplementCategory.DISTRIBUIDOR, HitchType.THREE_POINT, 40.0, 5.5, 320.0, 600.0, 11500.0, BLUE_NOGUEIRA, "Compacto para aplicação de sementes forrageiras e ureia.", null, "Distribuidores"),
    toImp("dist_jacto_tellus10000", "Jacto", "Tellus 10.000 NPK", ImplementCategory.DISTRIBUIDOR, HitchType.DRAWBAR, 110.0, 10.5, 2900.0, 10000.0, 138000.0, ORANGE_JACTO, "Distribuidor de arrasto de alta precisão até 36 metros de faixa.", null, "Distribuidores"),
    toImp("dist_jan_lancer15000", "Jan", "Lancer Maximus 15.000 Calcário", ImplementCategory.DISTRIBUIDOR, HitchType.DRAWBAR, 145.0, 11.0, 3600.0, 15000.0, 165000.0, GREEN_JAN, "Esteira dupla para calcário, gesso agrícola e adubo granulado.", null, "Distribuidores"),
    toImp("dist_stara_hercules10000", "Stara", "Hércules 10000 Inox", ImplementCategory.DISTRIBUIDOR, HitchType.DRAWBAR, 125.0, 12.0, 3200.0, 10000.0, 158000.0, ORANGE_JACTO, "Taxa variável integrada com comporta dupla independente.", null, "Distribuidores"),
    toImp("dist_stara_bruttus12000", "Stara", "Bruttus 12000 Gravidade", ImplementCategory.DISTRIBUIDOR, HitchType.DRAWBAR, 135.0, 8.8, 3450.0, 12000.0, 148000.0, ORANGE_JACTO, "Aplicação por gravidade sem deriva por vento ideal para calcário.", null, "Distribuidores"),
    toImp("dist_kuhn_axis50", "Kuhn", "Axis 50.2 EMC-W Hidráulico", ImplementCategory.DISTRIBUIDOR, HitchType.THREE_POINT, 105.0, 12.5, 980.0, 4200.0, 96000.0, rgb(0.82, 0.16, 0.14), "Balança eletrônica contínua para ajuste automático de dosagem.", null, "Distribuidores"),

    // Fenação, Silagem e Roçadeiras (14)
    toImp("rocadeira_baldan_17", "Baldan", "Roçadeira RPD 1.7m", ImplementCategory.ROCADEIRA, HitchType.THREE_POINT, 45.0, 1.8, 490.0, 0.0, 14500.0, RED_BALDAN, "Limpeza rápida de pastos, beira de estradas e entrelinhas.", null, "Roçadeiras"),
    toImp("rocadeira_jan_4m", "Jan", "Roçadeira Articulada Triple 4.2m", ImplementCategory.ROCADEIRA, HitchType.DRAWBAR, 110.0, 4.2, 1680.0, 0.0, 52000.0, GREEN_JAN, "Três módulos flutuantes para limpeza extensiva de pastagens.", null, "Roçadeiras"),
    toImp("segadora_claas_disco3600", "Claas", "Disco 3600 Contour", ImplementCategory.SEGADORA, HitchType.THREE_POINT, 85.0, 3.6, 1240.0, 0.0, 68000.0, C.CLAAS_GREEN, "Barra de corte Max Cut com condicionador para secagem rápida.", null, "Segadoras"),
    toImp("segadora_pottinger_9200", "Pöttinger", "Novacat A9 Tripla 9.2m", ImplementCategory.SEGADORA, HitchType.THREE_POINT, 190.0, 9.2, 3150.0, 0.0, 215000.0, rgb(0.82, 0.14, 0.14), "Segadora borboleta de altíssima produtividade para grandes fazendas de feno.", null, "Segadoras"),
    toImp("enleirador_kuhn_ga7501", "Kuhn", "GA 7501+ Duplo Rotor 7.5m", ImplementCategory.ENLEIRADOR, HitchType.DRAWBAR, 80.0, 7.5, 2100.0, 0.0, 112000.0, rgb(0.82, 0.16, 0.14), "Forma leiras centrais densas e arejadas sem contaminação por terra.", null, "Enleiradores"),
    toImp("enleirador_claas_liner2700", "Claas", "Liner 2700 Tandem 7.4m", ImplementCategory.ENLEIRADOR, HitchType.DRAWBAR, 75.0, 7.4, 1980.0, 0.0, 108000.0, C.CLAAS_GREEN, "Eixos tandem que copiam perfeitamente ondulações do pasto.", null, "Enleiradores"),
    toImp("enfardadora_jd_460m", "John Deere", "460M Redonda Silage", ImplementCategory.ENFARDADORA, HitchType.DRAWBAR, 95.0, 2.8, 3100.0, 1.0, 168000.0, C.JD_GREEN, "Fardos redondos de alta densidade com câmara variável.", null, "Enfardadoras"),
    toImp("enfardadora_claas_quad5200", "Claas", "Quadrant 5200 Fardo Gigante", ImplementCategory.ENFARDADORA, HitchType.DRAWBAR, 165.0, 3.2, 7400.0, 1.0, 485000.0, C.CLAAS_GREEN, "Produz megafardos prismáticos de alta tonelagem para logística rodoviária.", null, "Enfardadoras"),
    toImp("enfardadora_nogueira_4160", "Nogueira", "Express 4160 Retangular", ImplementCategory.ENFARDADORA, HitchType.DRAWBAR, 55.0, 2.2, 1650.0, 1.0, 86000.0, BLUE_NOGUEIRA, "Enfardadora nacional clássica para feno de tifton e alfafa.", null, "Enfardadoras"),
    toImp("ensiladeira_nogueira_pecus", "Nogueira", "Pecus 9004 1 Linha", ImplementCategory.ENSILADEIRA, HitchType.THREE_POINT, 65.0, 1.8, 720.0, 3200.0, 39000.0, BLUE_NOGUEIRA, "Tradicional ensiladeira nacional para pequenos e médios confinamentos.", null, "Ensiladeiras"),
    toImp("ensiladeira_jf_c240", "JF Máquinas", "JF C240 Dual 2 Linhas", ImplementCategory.ENSILADEIRA, HitchType.THREE_POINT, 110.0, 2.8, 1520.0, 5200.0, 88000.0, rgb(0.18, 0.56, 0.24), "Quebrador de grãos CrackerPro para silagem de altíssimo valor nutritivo.", null, "Ensiladeiras"),
    toImp("ensiladeira_casale_area", "Casale", "Area 4000 Colhedora Forragem", ImplementCategory.ENSILADEIRA, HitchType.DRAWBAR, 145.0, 3.4, 2400.0, 6800.0, 148000.0, rgb(0.84, 0.16, 0.14), "Duas linhas independentes com bica hidráulica orientável.", null, "Ensiladeiras"),
    toImp("ensacadora_marcher_200", "Marcher", "SiloPack Embolsadora 6 Pés", ImplementCategory.ENSACADORA, HitchType.DRAWBAR, 75.0, 2.4, 1950.0, 6000.0, 98000.0, rgb(0.18, 0.42, 0.72), "Embolsa grandes volumes de silagem em silo-bolsa contínuo.", null, "Ensacadoras"),
    toImp("ensacadora_pinheiro_compacta", "Pinheiro", "Ensacadora Agro 25/40kg", ImplementCategory.ENSACADORA, HitchType.THREE_POINT, 40.0, 1.8, 860.0, 1800.0, 31000.0, BLUE_NOGUEIRA, "Prática para comercialização de sacos de silagem de milho e capiaçu.", null, "Ensacadoras"),

    // Preparo Especial, Café, Mandioca, Cana e Nivelamento (14)
    toImp("arado_tatudisc_4d", "Tatu Marchesan", "Arado Reversível AR-4D", ImplementCategory.ARADO, HitchType.THREE_POINT, 65.0, 2.2, 760.0, 0.0, 21500.0, RED_TATU, "Arado reversível compacto para terrenos declivosos e encostas.", null, "Arados"),
    toImp("grade_baldan_cri24", "Baldan", "Grade Niveladora CRI 24D", ImplementCategory.GRADE, HitchType.DRAWBAR, 75.0, 3.0, 1320.0, 0.0, 29800.0, RED_BALDAN, "Acabamento de solo em lavouras familiares de feijão e milho.", null, "Grades"),
    toImp("subsolador_jan_5h_std", "Jan", "Subsolador Descompactador 5H", ImplementCategory.SUBSOLADOR, HitchType.THREE_POINT, 110.0, 2.8, 1280.0, 0.0, 34500.0, GREEN_JAN, "Hastes parabólicas para reduzir resistência mecânica à penetração.", null, "Subsoladores"),
    toImp("escarificador_baldan_7h", "Baldan", "Escarificador EB-7 Hastes", ImplementCategory.ESCARIFICADOR, HitchType.DRAWBAR, 140.0, 3.6, 1780.0, 0.0, 46500.0, RED_BALDAN, "Quebra crosta superficial preservando a cobertura de palha.", null, "Escarificadores"),
    toImp("plantadeira_planti_4l", "Planti Center", "Plantadeira de Mandioca Bazuka 2L", ImplementCategory.PLANTADEIRA, HitchType.THREE_POINT, 75.0, 2.2, 1150.0, 1200.0, 44000.0, rgb(0.18, 0.56, 0.24), "Plantadeira especializada de manivas de mandioca no Paraná e Nordeste.", null, "Plantadeiras"),
    toImp("plantadeira_dmb_cana", "DMB", "Plantadora de Cana PCP-2 Automatizada", ImplementCategory.PLANTADEIRA, HitchType.DRAWBAR, 175.0, 3.0, 4800.0, 4500.0, 195000.0, rgb(0.84, 0.28, 0.12), "Distribui mudas de cana-de-açúcar com aplicação simultânea de adubo.", null, "Plantadeiras"),
    toImp("semeadora_baldan_sp21", "Baldan", "Semeadora Fina SP-21 Arroz", ImplementCategory.SEMEADORA, HitchType.DRAWBAR, 95.0, 3.8, 2500.0, 1950.0, 79000.0, RED_BALDAN, "Construção reforçada para arroz irrigado em taipas do Sul.", null, "Semeadoras"),
    toImp("pulv_kuhn_fighter", "Kuhn", "Pulverizador Fighter Acoplado 1200L", ImplementCategory.PULVERIZADOR, HitchType.THREE_POINT, 80.0, 9.0, 1080.0, 1200.0, 62000.0, rgb(0.82, 0.16, 0.14), "Barras de 18 metros com fechamento hidráulico rápido.", null, "Pulverizadores"),
    toImp("dist_ikeda_calc2000", "Ikeda", "Distribuidor Orgânico DCO 2000", ImplementCategory.DISTRIBUIDOR, HitchType.THREE_POINT, 55.0, 4.5, 640.0, 2000.0, 26500.0, rgb(0.18, 0.44, 0.72), "Distribuição localizada de composto orgânico e esterco em cafezais.", null, "Distribuidores"),
    toImp("rocadeira_kuhn_tb210", "Kuhn", "Roçadeira Lateral TB 210 Talude", ImplementCategory.ROCADEIRA, HitchType.THREE_POINT, 85.0, 2.1, 890.0, 0.0, 56000.0, rgb(0.82, 0.16, 0.14), "Braço articulado para roçagem de barrancos, cercas e carreadores.", null, "Roçadeiras"),
    toImp("segadora_krone_ec320", "Krone", "EasyCut R 320 Condicionadora", ImplementCategory.SEGADORA, HitchType.THREE_POINT, 80.0, 3.2, 1140.0, 0.0, 64000.0, C.CLAAS_GREEN, "Barra soldada SmartCut de baixa manutenção para feno e pré-secado.", null, "Segadoras"),
    toImp("enleirador_nogueira_erg6", "Nogueira", "Enleirador Rotativo ER-420", ImplementCategory.ENLEIRADOR, HitchType.THREE_POINT, 45.0, 4.2, 560.0, 0.0, 29000.0, BLUE_NOGUEIRA, "Acionamento suave pela TDP com proteção dos dedos recolhedores.", null, "Enleiradores"),
    toImp("enfardadora_nh_bc5070", "New Holland", "Enfardadora Retangular BC5070", ImplementCategory.ENFARDADORA, HitchType.DRAWBAR, 75.0, 2.4, 2250.0, 1.0, 142000.0, rgb(0.95, 0.72, 0.08), "Fardos prismáticos compactos de feno e palhada de arroz.", null, "Enfardadoras"),
    toImp("ensiladeira_mepel_jf160", "JF Máquinas", "Colhedora Forragem JF C1600", ImplementCategory.ENSILADEIRA, HitchType.THREE_POINT, 90.0, 2.4, 1340.0, 4200.0, 72000.0, rgb(0.18, 0.56, 0.24), "Rotor de 12 facas para corte uniforme de milho, sorgo e capiaçu.", null, "Ensiladeiras"),
)

private val EXTRA_TRAILERS_ROWS: List<ImplementDef> = listOf(
    toImp("carreta_fankhauser_8t", "Fankhauser", "Carreta Agrícola 8t Madeira", ImplementCategory.CARRETA, HitchType.DRAWBAR, 50.0, 2.3, 1850.0, 9500.0, 38000.0, rgb(0.76, 0.28, 0.14), "Carreta clássica de carroceria de madeira reforçada.", null, "Carreta Graneleira"),
    toImp("carreta_baldan_10t", "Baldan", "Carreta Graneleira CAG 10t", ImplementCategory.CARRETA, HitchType.DRAWBAR, 60.0, 2.4, 2300.0, 12500.0, 54000.0, RED_BALDAN, "Versátil para sacaria, insumos e transferência curta de grãos.", null, "Carreta Graneleira"),
    toImp("carreta_jan_tanker16t", "Jan", "Tanker 16.000 Bazuca", ImplementCategory.CARRETA, HitchType.DRAWBAR, 130.0, 2.8, 3900.0, 19000.0, 118000.0, GREEN_JAN, "Carreta graneleira de descarga rápida por tubo sem-fim.", null, "Carreta Graneleira"),
    toImp("carreta_jan_tanker32t", "Jan", "Tanker Magno 32.000 Bazuca", ImplementCategory.CARRETA, HitchType.DRAWBAR, 240.0, 3.2, 7200.0, 38000.0, 245000.0, GREEN_JAN, "Gigante de 38.000L com dois eixos tandem e pneus de alta flutuação.", null, "Carreta Graneleira"),
    toImp("carreta_stara_reboke24", "Stara", "Reboke Inox 24000", ImplementCategory.CARRETA, HitchType.DRAWBAR, 185.0, 3.0, 5800.0, 29000.0, 198000.0, ORANGE_JACTO, "Caçamba inoxidável resistente a fertilizantes e descarga em 2 minutos.", null, "Carreta Graneleira"),
    toImp("carreta_stara_reboke36", "Stara", "Reboke Ninja 36000", ImplementCategory.CARRETA, HitchType.DRAWBAR, 270.0, 3.3, 8100.0, 42000.0, 278000.0, ORANGE_JACTO, "Máxima autonomia de transbordo acompanhando colheitadeiras Classe 8 e 9.", null, "Carreta Graneleira"),
    toImp("carreta_gts_grain40", "GTS", "Grain Liner 40.000L Esteiras", ImplementCategory.CARRETA, HitchType.DRAWBAR, 290.0, 3.4, 9400.0, 48000.0, 335000.0, rgb(0.88, 0.56, 0.10), "Equipada com esteiras de borracha para não compactar talhões úmidos.", null, "Carreta Graneleira"),
    toImp("carreta_librelato_rod35", "Librelato", "Semirreboque Graneleiro 3 Eixos", ImplementCategory.CARRETA, HitchType.FIFTH_WHEEL, 360.0, 2.55, 8600.0, 45000.0, 238000.0, WHITE, "Reboque rodoviário de 3 eixos para cavalo mecânico e balança.", null, "Carreta Graneleira"),
    toImp("carreta_randon_rodotrem", "Randon", "Rodo-Graneleiro 9 Eixos Tandem", ImplementCategory.CARRETA, HitchType.FIFTH_WHEEL, 480.0, 2.6, 13400.0, 68000.0, 420000.0, rgb(0.16, 0.34, 0.62), "Conjunto pesado para transporte maciço de safra até cooperativas e portos.", null, "Carreta Graneleira"),
    toImp("carreta_guerra_bitrem", "Guerra", "Bitrem Graneleiro 7 Eixos", ImplementCategory.CARRETA, HitchType.FIFTH_WHEEL, 420.0, 2.55, 11200.0, 56000.0, 348000.0, rgb(0.82, 0.14, 0.14), "Articulação dupla eficiente para rotas estaduais e rodovias.", null, "Carreta Graneleira"),
    toImp("carreta_facchini_granel", "Facchini", "LS Vanderleia Graneleira", ImplementCategory.CARRETA, HitchType.FIFTH_WHEEL, 390.0, 2.55, 9100.0, 49000.0, 275000.0, SILVER, "Eixos espaçados para maior capacidade legal de peso na balança.", null, "Carreta Graneleira"),
    toImp("carreta_noma_rodo_40", "Noma", "Titanium 4ª Eixo Graneleiro", ImplementCategory.CARRETA, HitchType.FIFTH_WHEEL, 400.0, 2.55, 9200.0, 52000.0, 289000.0, rgb(0.16, 0.44, 0.24), "Chassi leve em aço de alta resistência para maior carga útil.", null, "Carreta Graneleira"),

    toImp("carreta_randon_basc6", "Randon", "Basculante Agrícola 6t", ImplementCategory.CARRETA, HitchType.DRAWBAR, 55.0, 2.3, 1950.0, 7500.0, 39000.0, rgb(0.90, 0.62, 0.12), "Tombamento traseiro hidráulico ideal para milho, terra e adubo.", null, "Carreta Basculante"),
    toImp("carreta_noma_basc14", "Noma", "Basculante Hidráulica 14t", ImplementCategory.CARRETA, HitchType.DRAWBAR, 110.0, 2.6, 3600.0, 17500.0, 89000.0, rgb(0.18, 0.44, 0.24), "Caçamba metálica reforçada com pistão frontal de alta elevação.", null, "Carreta Basculante"),
    toImp("carreta_krone_silagem44", "Krone", "TX 460 D Vagão Silagem", ImplementCategory.CARRETA, HitchType.DRAWBAR, 175.0, 2.8, 6900.0, 46000.0, 248000.0, C.CLAAS_GREEN, "Vagão forrageiro com esteira de descarga rápida no silo trincheira.", null, "Carreta Forrageira"),
    toImp("carreta_nogueira_vf12", "Nogueira", "Vagão Forrageiro NVA 12m³", ImplementCategory.CARRETA, HitchType.DRAWBAR, 75.0, 2.4, 2400.0, 12000.0, 68000.0, BLUE_NOGUEIRA, "Transporte de milho picado e capiaçu do campo direto ao curral.", null, "Carreta Forrageira"),
    toImp("carreta_casale_mix20", "Casale", "Vagão Misturador TotalMix 20m³", ImplementCategory.CARRETA, HitchType.DRAWBAR, 125.0, 2.55, 5800.0, 20000.0, 185000.0, rgb(0.84, 0.16, 0.14), "Prepara ração total misturada (TMR) para distribuição nos cochos.", null, "Carreta Forrageira"),
    toImp("carreta_santon_cana22", "Santon", "Transbordo Canavieiro 22t", ImplementCategory.CARRETA, HitchType.DRAWBAR, 195.0, 3.0, 7600.0, 28000.0, 215000.0, rgb(0.86, 0.38, 0.08), "Elevação lateral para descarregar cana picada direto em caminhões.", null, "Carreta Basculante"),
    toImp("carreta_sermag_cana16", "Sermag", "Transbordo Cana 16t", ImplementCategory.CARRETA, HitchType.DRAWBAR, 150.0, 2.8, 5900.0, 20000.0, 168000.0, rgb(0.90, 0.62, 0.12), "Pneus de alta flutuação para acompanhar colhedoras em sulcos úmidos.", null, "Carreta Basculante"),
    toImp("carreta_randon_rodobasc", "Randon", "Semirreboque Basculante 30m³", ImplementCategory.CARRETA, HitchType.FIFTH_WHEEL, 360.0, 2.55, 8200.0, 36000.0, 242000.0, DARK, "Basculante pesada de cavalo mecânico para calcário, pedra e grãos.", null, "Carreta Basculante"),
    toImp("carreta_pastre_rodo26", "Pastre", "Caçamba Basculante 26m³", ImplementCategory.CARRETA, HitchType.FIFTH_WHEEL, 340.0, 2.55, 7900.0, 32000.0, 224000.0, rgb(0.76, 0.14, 0.14), "Fundo reforçado em aço Hardox contra impacto de rochas e terra.", null, "Carreta Basculante"),
    toImp("carreta_fliegl_push", "Fliegl", "ASW 271 Empurradora Gigante", ImplementCategory.CARRETA, HitchType.DRAWBAR, 200.0, 2.75, 7100.0, 35000.0, 232000.0, rgb(0.22, 0.48, 0.22), "Descarga por parede hidráulica sem tombar a caçamba, ideal dentro de galpões.", null, "Carreta Basculante"),

    toImp("carreta_pipa_6000", "Mepel", "Carreta Pipa Tanque 6.000L", ImplementCategory.CARRETA, HitchType.DRAWBAR, 65.0, 2.3, 1850.0, 6000.0, 44000.0, rgb(0.16, 0.48, 0.78), "Abastecimento de água para pulverizadores e lavagem no campo.", null, "Carreta Tanque"),
    toImp("carreta_chorume_12000", "Ipacol", "Distribuidor Líquido DL 12.000L", ImplementCategory.CARRETA, HitchType.DRAWBAR, 115.0, 4.5, 3400.0, 12000.0, 96000.0, rgb(0.24, 0.46, 0.24), "Aplicação de dejetos líquidos e biofertilizantes em pastagens.", null, "Carreta Tanque"),
    toImp("carreta_joskin_22000", "Joskin", "Modulo2 22.000L Tanque Inox", ImplementCategory.CARRETA, HitchType.DRAWBAR, 185.0, 6.0, 5600.0, 22000.0, 188000.0, rgb(0.22, 0.56, 0.26), "Tanque pressurizado tandem com barra injetora de nutrientes.", null, "Carreta Tanque"),
    toImp("carreta_leite_28000", "Triel-HT", "Semirreboque Tanque Leiteiro 28.000L", ImplementCategory.CARRETA, HitchType.FIFTH_WHEEL, 340.0, 2.55, 7800.0, 28000.0, 310000.0, SILVER, "Tanque isotérmico em aço inox para coleta e venda de leite.", null, "Carreta Tanque"),
    toImp("carreta_combustivel_18000", "Randon", "Tanque Comboio Diesel 18.000L", ImplementCategory.CARRETA, HitchType.FIFTH_WHEEL, 300.0, 2.55, 7100.0, 18000.0, 265000.0, rgb(0.88, 0.62, 0.12), "Abastecimento móvel de óleo diesel S10 para colheitadeiras no talhão.", null, "Carreta Tanque"),
    toImp("carreta_prancha_fardo8", "Noma", "Prancha Agrícola Fardos 8m", ImplementCategory.CARRETA, HitchType.DRAWBAR, 75.0, 2.5, 2400.0, 14000.0, 49000.0, rgb(0.18, 0.44, 0.24), "Plataforma aberta para transporte de fardos redondos, retangulares e bags.", null, "Prancha / Plataforma"),
    toImp("carreta_prancha_bigbag12", "Facchini", "Prancha Plataforma Sementes 12m", ImplementCategory.CARRETA, HitchType.DRAWBAR, 120.0, 2.55, 3900.0, 22000.0, 82000.0, BLUE_NOGUEIRA, "Transporte de big bags de adubo, sementes e sacos de silagem.", null, "Prancha / Plataforma"),
    toImp("carreta_prancha_baixa35t", "Randon", "Prancha Carrega-Tudo 3 Eixos 35t", ImplementCategory.CARRETA, HitchType.FIFTH_WHEEL, 360.0, 3.0, 9800.0, 45000.0, 268000.0, DARK, "Rampas hidráulicas para transportar tratores, escavadeiras e colheitadeiras.", null, "Prancha / Baixa"),
    toImp("carreta_prancha_pesada60t", "Noma", "Prancha Pesada 4 Eixos 60t", ImplementCategory.CARRETA, HitchType.FIFTH_WHEEL, 480.0, 3.2, 13200.0, 70000.0, 395000.0, RED_BALDAN, "Eixos direcionais para transporte de tratores de esteira e colheitadeiras largas.", null, "Prancha / Baixa"),
    toImp("carreta_plataforma_header", "Schumacher", "Carreta Transporte de Plataforma 45 Pés", ImplementCategory.CARRETA, HitchType.DRAWBAR, 85.0, 2.4, 2100.0, 8000.0, 52000.0, C.JD_GREEN, "Engate dianteiro e traseiro para deslocar plataformas de corte em estradas.", null, "Prancha / Plataforma"),
    toImp("carreta_boiadeira_trac", "Boiforte", "Carreta Boiadeira Tratorizada 10 Cab.", ImplementCategory.CARRETA, HitchType.DRAWBAR, 80.0, 2.45, 2600.0, 8000.0, 64000.0, rgb(0.46, 0.26, 0.14), "Piso antiderrapante para movimentar gado entre pastos e curral.", null, "Carreta Boiadeira"),
    toImp("carreta_boiadeira_2piso", "Triel-HT", "Semirreboque Boiadeiro 2 Pisos", ImplementCategory.CARRETA, HitchType.FIFTH_WHEEL, 380.0, 2.55, 9900.0, 32000.0, 295000.0, rgb(0.76, 0.14, 0.14), "Transporte rodoviário de alto volume para bovinos e ovinos.", null, "Carreta Boiadeira"),
    toImp("carreta_florestal_grua12", "Peninsula", "Reboque Florestal c/ Grua 12t", ImplementCategory.CARRETA, HitchType.DRAWBAR, 115.0, 2.4, 3400.0, 14000.0, 128000.0, rgb(0.22, 0.44, 0.22), "Fueiros reforçados e grua acoplada para recolher toras de eucalipto e pinus.", null, "Carreta Florestal"),
    toImp("carreta_florestal_rodo", "Randon", "Semirreboque Florestal Tri-Fueiro", ImplementCategory.CARRETA, HitchType.FIFTH_WHEEL, 420.0, 2.55, 8900.0, 48000.0, 245000.0, DARK, "Transporte de grandes cargas de madeira até serrarias e fábricas de celulose.", null, "Carreta Florestal"),
    toImp("carreta_agua_inox5000", "Fockink", "Tanque Irrigação Móvel 5.000L", ImplementCategory.CARRETA, HitchType.DRAWBAR, 60.0, 2.2, 1650.0, 5000.0, 42000.0, rgb(0.14, 0.48, 0.78), "Motobomba acoplada à TDP para irrigação de mudas e hortaliças.", null, "Carreta Tanque"),
    toImp("carreta_dolly_agro", "Randon", "Dolly Reboque Quinta Roda", ImplementCategory.CARRETA, HitchType.DRAWBAR, 140.0, 2.4, 2300.0, 12000.0, 48000.0, DARK, "Permite acoplar semirreboques quinta-roda diretamente em tratores pesados.", null, "Dolly / Acoplador"),
    toImp("carreta_tatu_granel14", "Tatu Marchesan", "Carreta Basculante Graneleira 14t", ImplementCategory.CARRETA, HitchType.DRAWBAR, 110.0, 2.6, 3400.0, 16000.0, 78000.0, RED_TATU, "Caçamba agrícola com sobre-tampa removível para soja, milho e adubo.", null, "Carreta Graneleira"),
    toImp("carreta_kuhn_transbordo28", "Kuhn", "Bazuca Graneleira 28.000L", ImplementCategory.CARRETA, HitchType.DRAWBAR, 210.0, 3.1, 6400.0, 28000.0, 212000.0, rgb(0.82, 0.16, 0.14), "Tubo telescópico de 450 mm para descarregar direto em bitrens na cabeceira.", null, "Carreta Graneleira"),
    toImp("carreta_noma_tanque_agro", "Noma", "Tanque Cisterna Inox 15.000L", ImplementCategory.CARRETA, HitchType.DRAWBAR, 140.0, 2.5, 4100.0, 15000.0, 134000.0, SILVER, "Abastecimento de água potável e calda pronta para pulverizadores no campo.", null, "Carreta Tanque"),
    toImp("carreta_sermag_silagem20", "Sermag", "Vagão Forrageiro Basculante 20t", ImplementCategory.CARRETA, HitchType.DRAWBAR, 155.0, 2.7, 5200.0, 24000.0, 149000.0, rgb(0.90, 0.62, 0.12), "Telas laterais altas para acompanhar ensiladeiras na colheita de capiaçu e milho.", null, "Carreta Forrageira"),

    // ModHub
    toImp("carreta_bazuca_jan_tanker24_mod", "Jan", "Bazuca Tanker 24.000L ModHub HD", ImplementCategory.CARRETA, HitchType.DRAWBAR, 175.0, 2.9, 5400.0, 28000.0, 172000.0, GREEN_JAN, "Carreta graneleira com rosca sem-fim telescópica e pneus de alta flutuação.", "pack_bazucas_graneleiras_hd", "Carreta Graneleira"),
    toImp("carreta_bazuca_stara_reboke24_mod", "Stara", "Reboke 24.000 Ninja Inox HD", ImplementCategory.CARRETA, HitchType.DRAWBAR, 185.0, 3.0, 5700.0, 29000.0, 198000.0, ORANGE_JACTO, "Caçamba inoxidável anti-corrosiva para transbordo ágil em soja e milho.", "pack_bazucas_graneleiras_hd", "Carreta Graneleira"),
    toImp("pulv_jacto_advance3000_mod", "Jacto", "Advance 3000 AM24 Vortext ModHub", ImplementCategory.PULVERIZADOR, HitchType.DRAWBAR, 95.0, 12.0, 2950.0, 3000.0, 142000.0, ORANGE_JACTO, "Pulverizador de arrasto com assistência a ar Vortext e barra de 24 metros.", "pack_implementos_jacto_stara_hd", "Pulverizadores"),
    toImp("semeadora_stara_cinderela_mod", "Stara", "Cinderela 15 Linhas Pantográfica", ImplementCategory.PLANTADEIRA, HitchType.DRAWBAR, 155.0, 6.8, 5200.0, 3200.0, 188000.0, ORANGE_JACTO, "Plantadeira articulada com corte de seção e linha pantográfica de precisão.", "pack_implementos_jacto_stara_hd", "Plantadeiras"),
    toImp("rocadeira_jan_inox_mod", "Jan", "Roçadeira Hidráulica Heavy 3.2m", ImplementCategory.ROCADEIRA, HitchType.THREE_POINT, 75.0, 3.2, 920.0, 0.0, 29500.0, GREEN_JAN, "Roçadeira dupla transmissão para limpeza de pastagens e entrelinhas.", "pack_feno_agropecuario_hd", "Roçadeiras"),
    toImp("enfardadora_krone_bigpack_mod", "Krone", "BiG Pack 1290 HDP II", ImplementCategory.ENFARDADORA, HitchType.DRAWBAR, 220.0, 3.4, 9800.0, 1.0, 540000.0, C.CLAAS_GREEN, "Enfardadora prismática gigante de alta densidade para grandes fazendas de feno.", "pack_feno_agropecuario_hd", "Enfardadoras"),
)

val NEW_AGRITECH_IMPLEMENTS_24: List<ImplementDef> = listOf(
    // 1. Arado de discos
    toImp("arado_ikeda_5d_disco", "Ikeda", "Arado de Discos Hidráulico 5D", ImplementCategory.ARADO, HitchType.THREE_POINT, 82.0, 2.7, 920.0, 0.0, 24800.0, rgb(0.18, 0.44, 0.72), "Arado de discos recortados 28 polegadas para solos pedregosos e raízes.", null, "Arado de discos"),
    // 2. Arado de aivecas
    toImp("arado_kverneland_5_aiveca", "Kverneland", "Aiveca Reversível ES-85 5 Corpos", ImplementCategory.ARADO, HitchType.THREE_POINT, 145.0, 3.1, 1690.0, 0.0, 58500.0, rgb(0.84, 0.16, 0.14), "Arado de aivecas reversível com desarme hidráulico para inversão limpa de leiva.", null, "Arado de aivecas"),
    // 3. Grade aradora
    toImp("grade_piccin_aradora_24d", "Piccin", "Grade Aradora GAI-C 24 Discos", ImplementCategory.GRADE, HitchType.DRAWBAR, 115.0, 3.2, 2380.0, 0.0, 49500.0, rgb(0.82, 0.48, 0.12), "Grade aradora pesada com pneus de transporte e mancais a banho de óleo.", null, "Grade aradora"),
    // 4. Grade niveladora
    toImp("grade_tatu_niveladora_44d", "Tatu Marchesan", "Grade Niveladora GNCR 44 Discos", ImplementCategory.GRADE, HitchType.DRAWBAR, 140.0, 5.1, 2940.0, 0.0, 69500.0, RED_TATU, "Grade niveladora articulada de alta precisão para acabamento pós-aração.", null, "Grade niveladora"),
    // 5. Subsolador
    toImp("subsolador_tatu_7h_canavieiro", "Tatu Marchesan", "Subsolador Canavieiro AST 7 Hastes", ImplementCategory.SUBSOLADOR, HitchType.DRAWBAR, 205.0, 4.1, 2980.0, 0.0, 81000.0, RED_TATU, "Subsolador reforçado com discos cortadores de palhada e rolo destorroador.", null, "Subsolador"),
    // 6. Escarificador
    toImp("escarificador_ikeda_9h_pantanal", "Ikeda", "Escarificador Haste Parabólica 9H", ImplementCategory.ESCARIFICADOR, HitchType.DRAWBAR, 165.0, 4.3, 2150.0, 0.0, 54000.0, BLUE_NOGUEIRA, "Escarificador de baixo arrasto para descompactação superficial com preservação da palha.", null, "Escarificador"),
    // 7 & 8. Cultivadores (nova categoria dedicada)
    toImp("cultivador_baldan_9_enxadas", "Baldan", "Cultivador Entrelinhas 9 Enxadas", ImplementCategory.CULTIVADOR, HitchType.THREE_POINT, 65.0, 4.0, 740.0, 0.0, 22400.0, RED_BALDAN, "Cultivador mecânico de entrelinhas que remove ervas daninhas sem destruir a lavoura.", null, "Cultivador"),
    toImp("cultivador_fb_original_13h", "Farm Brasil", "Cultivador Híbrido AgroSul 13H", ImplementCategory.CULTIVADOR, HitchType.DRAWBAR, 125.0, 5.8, 1620.0, 0.0, 41500.0, rgb(0.14, 0.54, 0.24), "Modelo original Farm Brasil: hastes vibratórias com rolo destorroador para capina mecânica veloz.", null, "Cultivador • Modelo original Farm Brasil"),
    // 9. Plantadeira de milho
    toImp("plantadeira_tatu_ultra_milho_15l", "Tatu Marchesan", "UltraFlex Milho 15 Linhas Vácuo", ImplementCategory.PLANTADEIRA, HitchType.DRAWBAR, 165.0, 6.8, 5600.0, 3400.0, 218000.0, RED_TATU, "Plantadeira de milho de precisão pneumática com espaçamento de 45/50 cm.", null, "Plantadeira de milho"),
    // 10. Plantadeira de soja
    toImp("plantadeira_stara_estrela_soja_20l", "Stara", "Sovereign Soja 20 Linhas Articulada", ImplementCategory.PLANTADEIRA, HitchType.DRAWBAR, 220.0, 9.0, 7600.0, 4200.0, 312000.0, ORANGE_JACTO, "Plantadeira de soja pantográfica com caixa central de sementes e dosador pneumático.", null, "Plantadeira de soja"),
    // 11. Plantadeira de grãos
    toImp("plantadeira_jumil_graos_17l", "Jumil", "Terra Grãos Exacta 17 Linhas", ImplementCategory.PLANTADEIRA, HitchType.DRAWBAR, 180.0, 7.6, 6100.0, 3600.0, 238000.0, RED_BALDAN, "Plantadeira multi-grãos para soja, milho, feijão e sorgo em plantio direto.", null, "Plantadeira de grãos"),
    // 12. Semeadora
    toImp("semeadora_semeato_tdng_320", "Semeato", "TDNG 320 Semeadora Fluxo Fino", ImplementCategory.SEMEADORA, HitchType.DRAWBAR, 90.0, 4.4, 2850.0, 2100.0, 84500.0, GREEN_JAN, "Semeadora clássica de plantio direto para trigo, aveia, arroz e pastagens.", null, "Semeadora"),
    // 13. Distribuidor de fertilizantes
    toImp("dist_jan_lancer_inox_8000", "Jan", "Lancer Inox 8.000 Duplo Disco", ImplementCategory.DISTRIBUIDOR, HitchType.DRAWBAR, 105.0, 11.0, 2480.0, 8000.0, 114000.0, GREEN_JAN, "Distribuidor centrífugo em inox para adubos NPK, ureia e corretivos agrícolas.", null, "Distribuidor de fertilizantes"),
    // 14. Pulverizador de barras
    toImp("pulv_stara_gladiador_27m", "Stara", "Gladiador 2500 Barras 27m", ImplementCategory.PULVERIZADOR, HitchType.DRAWBAR, 105.0, 11.2, 2750.0, 2500.0, 134000.0, ORANGE_JACTO, "Pulverizador de barras hidráulicas de 27 metros com estabilidade pendular.", null, "Pulverizador de barras"),
    // 15. Pulverizador de arrasto
    toImp("pulv_jacto_coral_2000_arrasto", "Jacto", "Coral Tandem 2000 de Arrasto", ImplementCategory.PULVERIZADOR, HitchType.DRAWBAR, 80.0, 8.8, 2150.0, 2000.0, 96000.0, ORANGE_JACTO, "Pulverizador de arrasto com eixo tandem para maior estabilidade em curvas de nível.", null, "Pulverizador de arrasto"),
    // 16. Carreta agrícola
    toImp("carreta_piccin_agricola_12t", "Piccin", "Carreta Agrícola Multiflex 12t", ImplementCategory.CARRETA, HitchType.DRAWBAR, 75.0, 2.5, 2550.0, 14500.0, 58000.0, rgb(0.82, 0.48, 0.12), "Carreta agrícola reforçada para sementes, sacaria, grãos e insumos na fazenda.", null, "Carreta agrícola"),
    // 17. Carreta basculante
    toImp("carreta_facchini_basc_18t", "Facchini", "Carreta Basculante Agro 18t", ImplementCategory.CARRETA, HitchType.DRAWBAR, 145.0, 2.7, 4650.0, 22000.0, 128000.0, SILVER, "Carreta basculante hidráulica de chassi reforçado para grãos, silagem e calcário.", null, "Carreta basculante"),
    // 18. Enfardadora
    toImp("enfardadora_kuhn_vb3160", "Kuhn", "VB 3160 Enfardadora Câmara Variável", ImplementCategory.ENFARDADORA, HitchType.DRAWBAR, 105.0, 2.8, 3450.0, 1.0, 189000.0, RED_BALDAN, "Enfardadora de rolos densos com recolhedor largo de alta capacidade.", null, "Enfardadora"),
    // 19. Segadora
    toImp("segadora_kuhn_gmd310", "Kuhn", "GMD 310 FF Segadora de Discos", ImplementCategory.SEGADORA, HitchType.THREE_POINT, 70.0, 3.1, 940.0, 0.0, 49500.0, RED_BALDAN, "Segadora frontal/traseira de alta velocidade para corte limpo de pastagens e feno.", null, "Segadora"),
    // 20. Ancinho enleirador
    toImp("enleirador_fb_ancinho_540", "Farm Brasil", "Ancinho Enleirador Girassol 5.4m", ImplementCategory.ENLEIRADOR, HitchType.DRAWBAR, 55.0, 5.4, 1180.0, 0.0, 44000.0, rgb(0.14, 0.54, 0.24), "Modelo original Farm Brasil: ancinho enleirador duplo para juntar feno sem contaminar com terra.", null, "Ancinho enleirador • Modelo original Farm Brasil"),
    // 21 & 22. Plataformas de milho (nova categoria dedicada p/ colheitadeiras)
    toImp("plataforma_milho_gts_12l", "GTS", "Produtiva 12 Linhas Milho", ImplementCategory.PLATAFORMA_MILHO, HitchType.HEADER, 175.0, 6.0, 3450.0, 0.0, 215000.0, rgb(0.88, 0.56, 0.10), "Plataforma de milho nacional de 12 linhas em 45/50 cm com bicos basculantes.", null, "Plataforma de milho"),
    toImp("plataforma_milho_jd_616c", "John Deere", "616C Plataforma de Milho 16L", ImplementCategory.PLATAFORMA_MILHO, HitchType.HEADER, 340.0, 7.8, 4950.0, 0.0, 368000.0, C.JD_GREEN, "Plataforma de milho de alto desempenho para colheitadeiras axiais grandes.", null, "Plataforma de milho"),
    // 23 & 24. ModHub bônus implementos (desbloqueados pelos novos pacotes do Catálogo de Mods)
    toImp("cultivador_jan_canavieiro_mod", "Jan", "Cultivador Quebra-Lombo 5L ModHub", ImplementCategory.CULTIVADOR, HitchType.THREE_POINT, 115.0, 4.8, 1380.0, 0.0, 46000.0, GREEN_JAN, "Mod Cultivadores & Arados BR: Cultivador pesado de 5 linhas para cana e milho.", "pack_implementos_cultivadores_arados_br", "Cultivador"),
    toImp("plataforma_milho_stara_brava_mod", "Stara", "Brava+ 14 Linhas Milho ModHub", ImplementCategory.PLATAFORMA_MILHO, HitchType.HEADER, 240.0, 7.0, 3980.0, 0.0, 285000.0, ORANGE_JACTO, "Mod Plataformas de Milho Safra: Plataforma leve com carenagem em polietileno de alta durabilidade.", "pack_plataformas_milho_safra", "Plataforma de milho"),
)

val BRAZILIAN_RURAL_EXPANSION_CATALOG: List<ImplementDef> = listOf(
    // ==========================================
    // PARTE 2: SUBSOLADORES & ESCARIFICADORES (12)
    // ==========================================
    toImp("subsolador_baldan_crsg_5h", "Baldan", "Subsolador CRSG 5 Hastes Hidráulico", ImplementCategory.SUBSOLADOR, HitchType.THREE_POINT, 110.0, 2.2, 1150.0, 0.0, 31500.0, RED_BALDAN, "Subsolador hidráulico com desarme automático por mola helicoidal e ponteiras aladas.", null, "Subsolador"),
    toImp("subsolador_baldan_crsg_7h", "Baldan", "Subsolador CRSG 7 Hastes Hidráulico", ImplementCategory.SUBSOLADOR, HitchType.THREE_POINT, 160.0, 3.1, 1580.0, 0.0, 44000.0, RED_BALDAN, "Descompactação até 45cm de profundidade para solos argilosos e médios.", null, "Subsolador"),
    toImp("subsolador_baldan_spbr_9h", "Baldan", "Subsolador SPBR 9 Hastes de Arrasto", ImplementCategory.SUBSOLADOR, HitchType.DRAWBAR, 230.0, 4.2, 2900.0, 0.0, 79500.0, RED_BALDAN, "Subsolador de arrasto com controle de profundidade por rodas reguladoras hidráulicas.", null, "Subsolador"),
    toImp("subsolador_mecrul_spmr_5h", "Mec-Rul", "Subsolador SPMR 5 Hastes Compacto", ImplementCategory.SUBSOLADOR, HitchType.THREE_POINT, 95.0, 2.0, 980.0, 0.0, 27800.0, RED_MEC_RUL, "Subsolador leve para tratores médios em pequenas e médias lavouras.", null, "Subsolador"),
    toImp("subsolador_mecrul_spmr_7h", "Mec-Rul", "Subsolador SPMR 7 Hastes Reforçado", ImplementCategory.SUBSOLADOR, HitchType.THREE_POINT, 150.0, 2.9, 1420.0, 0.0, 39500.0, RED_MEC_RUL, "Hastes em aço tratado com parafuso fusível e discos de corte de palha.", null, "Subsolador"),
    toImp("subsolador_sao_jose_ssp_5h", "São José", "Subsolador SSP 5 Hastes", ImplementCategory.SUBSOLADOR, HitchType.THREE_POINT, 100.0, 2.1, 1080.0, 0.0, 29800.0, BLUE_SAO_JOSE, "Subsolador nacional com hastes parabólicas de alta penetração em solos compactados.", null, "Subsolador"),
    toImp("subsolador_sao_jose_ssp_7h", "São José", "Subsolador SSP 7 Hastes Arrasto", ImplementCategory.SUBSOLADOR, HitchType.DRAWBAR, 175.0, 3.2, 2100.0, 0.0, 56000.0, BLUE_SAO_JOSE, "Arrasto com cilindro hidráulico de levante e rolo destorroador de barras chatas.", null, "Subsolador"),
    toImp("subsolador_sao_jose_ssp_9h", "São José", "Subsolador SSP 9 Hastes Gigante", ImplementCategory.SUBSOLADOR, HitchType.DRAWBAR, 250.0, 4.3, 3350.0, 0.0, 88000.0, BLUE_SAO_JOSE, "Chassi monobloco reforçado para abertura de área e cana-de-açúcar.", null, "Subsolador"),
    toImp("escarificador_baldan_epa_7h", "Baldan", "Escarificador EPA 7 Hastes Hidráulico", ImplementCategory.ESCARIFICADOR, HitchType.THREE_POINT, 120.0, 2.8, 1450.0, 0.0, 36500.0, RED_BALDAN, "Escarificador com rolo destorroador traseiro e molas de desarme contínuo.", null, "Escarificador"),
    toImp("escarificador_baldan_epa_11h", "Baldan", "Escarificador EPA 11 Hastes de Arrasto", ImplementCategory.ESCARIFICADOR, HitchType.DRAWBAR, 190.0, 4.4, 2800.0, 0.0, 72000.0, RED_BALDAN, "Descompactador superficial de alta largura que preserva 85% da palhada na superfície.", null, "Escarificador"),
    toImp("escarificador_sao_jose_esp_7h", "São José", "Escarificador ESP 7 Hastes 3 Pontos", ImplementCategory.ESCARIFICADOR, HitchType.THREE_POINT, 115.0, 2.7, 1380.0, 0.0, 34500.0, BLUE_SAO_JOSE, "Hastes helicoidais com rolo cage de nivelamento e profundidade ajustável até 30cm.", null, "Escarificador"),
    toImp("escarificador_sao_jose_esp_9h", "São José", "Escarificador ESP 9 Hastes Arrasto", ImplementCategory.ESCARIFICADOR, HitchType.DRAWBAR, 165.0, 3.6, 2350.0, 0.0, 58000.0, BLUE_SAO_JOSE, "Articulado com discos de corte independentes e rolo duplo tandem.", null, "Escarificador"),

    // ==========================================
    // PARTE 3: GRADES AGRÍCOLAS & GRADE ARRASTÃO (11)
    // ==========================================
    toImp("grade_aradora_baldan_cri16", "Baldan", "Grade Aradora CRI 16 Discos", ImplementCategory.GRADE, HitchType.DRAWBAR, 90.0, 2.4, 1650.0, 0.0, 32000.0, RED_BALDAN, "Grade aradora com controle remoto hidráulico e discos recortados de 26 polegadas.", null, "Grade aradora"),
    toImp("grade_aradora_baldan_gai20", "Baldan", "Grade Aradora GAI 20 Discos", ImplementCategory.GRADE, HitchType.DRAWBAR, 125.0, 2.9, 2150.0, 0.0, 43500.0, RED_BALDAN, "Mancais em banho de óleo com blindagem reforçada para solos abrasivos e pedregosos.", null, "Grade aradora"),
    toImp("grade_niveladora_baldan_nvcr36", "Baldan", "Grade Niveladora NVCR 36 Discos", ImplementCategory.GRADE, HitchType.DRAWBAR, 105.0, 4.2, 1980.0, 0.0, 41000.0, RED_BALDAN, "Grade niveladora de arrasto com pistão hidráulico para transporte em estradas.", null, "Grade niveladora"),
    toImp("grade_niveladora_baldan_nvcr48", "Baldan", "Grade Niveladora NVCR 48 Discos", ImplementCategory.GRADE, HitchType.DRAWBAR, 155.0, 5.6, 2950.0, 0.0, 64000.0, RED_BALDAN, "Niveladora articulada de alto rendimento operacional para cama de semeadura.", null, "Grade niveladora"),
    toImp("grade_aradora_mecrul_gap18", "Mec-Rul", "Grade Aradora GAP 18 Discos", ImplementCategory.GRADE, HitchType.DRAWBAR, 110.0, 2.6, 1850.0, 0.0, 37000.0, RED_MEC_RUL, "Grade aradora intermediária de alta agressividade e corte profundo de torrões.", null, "Grade aradora"),
    toImp("grade_niveladora_mecrul_gnp32", "Mec-Rul", "Grade Niveladora GNP 32 Discos", ImplementCategory.GRADE, HitchType.DRAWBAR, 95.0, 3.8, 1720.0, 0.0, 36500.0, RED_MEC_RUL, "Nivelamento fino e acabamento para plantadeiras convencionais e diretas.", null, "Grade niveladora"),
    toImp("grade_aradora_anzabel_ga16", "Anzabel", "Grade Aradora Anzabel GA 16D", ImplementCategory.GRADE, HitchType.DRAWBAR, 85.0, 2.3, 1480.0, 0.0, 28500.0, GREEN_ANZABEL, "Equipamento tradicional de fabricação nacional para preparo primário de solo.", null, "Grade aradora"),
    toImp("grade_niveladora_anzabel_gn28", "Anzabel", "Grade Niveladora Anzabel GN 28D", ImplementCategory.GRADE, HitchType.DRAWBAR, 75.0, 3.2, 1340.0, 0.0, 26000.0, GREEN_ANZABEL, "Grade niveladora ágil para pequenas e médias propriedades rurais.", null, "Grade niveladora"),
    toImp("grade_pesada_baldan_super32", "Baldan", "Super Grade Pesada Articulada 32x34\"", ImplementCategory.GRADE, HitchType.DRAWBAR, 260.0, 4.8, 5400.0, 0.0, 135000.0, RED_BALDAN, "Grade pesada com discos gigantes de 34 polegadas para reforma de pasto e cana.", null, "Grade pesada"),
    toImp("grade_arrastao_nacional_40d", "Baldan", "Grade Arrastão Nacional 40 Discos", ImplementCategory.GRADE_ARRASTAO, HitchType.DRAWBAR, 220.0, 6.2, 4200.0, 0.0, 94000.0, RED_BALDAN, "Grade arrastão em tandem articulado: destorroamento bruto e nivelamento em alta escala com discos recortados.", null, "Grade arrastão"),
    toImp("grade_arrastao_safra_56d", "São José", "Grade Arrastão Safra Brasil 56 Discos", ImplementCategory.GRADE_ARRASTAO, HitchType.DRAWBAR, 320.0, 8.4, 6200.0, 0.0, 158000.0, BLUE_SAO_JOSE, "Grade arrastão gigante para grandes talhões no Centro-Oeste e Sul, cobrindo 8,4 metros por passada.", null, "Grade arrastão"),

    // ==========================================
    // PARTE 4: ARADOS & CULTIVADORES (13)
    // ==========================================
    toImp("arado_antigo_baldan_2d", "Baldan", "Arado Antigo Fixo 2 Discos", ImplementCategory.ARADO, HitchType.THREE_POINT, 28.0, 1.2, 380.0, 0.0, 8500.0, RED_BALDAN, "Arado clássico dos anos 60/70 para tratores pequenos (MF 50x, Ford 8N, Valmet 360).", null, "Arado antigo"),
    toImp("arado_antigo_tatu_3d", "Tatu Marchesan", "Arado Antigo 3 Discos Reversível", ImplementCategory.ARADO, HitchType.THREE_POINT, 42.0, 1.6, 520.0, 0.0, 11800.0, RED_TATU, "Clássico nacional com catraca mecânica de reversão e discos côncavos originais.", null, "Arado antigo"),
    toImp("arado_aiveca_baldan_3c", "Baldan", "Arado de Aiveca AV 3 Corpos", ImplementCategory.ARADO, HitchType.THREE_POINT, 60.0, 1.5, 680.0, 0.0, 17200.0, RED_BALDAN, "Arado de aiveca tradicional para inversão total da camada arável e controle de daninhas.", null, "Arado de aiveca"),
    toImp("arado_aiveca_tatu_5c_hidro", "Tatu Marchesan", "Arado de Aiveca AR-A 5 Corpos Hidro", ImplementCategory.ARADO, HitchType.THREE_POINT, 115.0, 2.5, 1280.0, 0.0, 34500.0, RED_TATU, "Reversão hidráulica automática de 180 graus com desarme mecânico de segurança.", null, "Arado de aiveca"),
    toImp("cultivador_enxadas_baldan_7l", "Baldan", "Cultivador de Enxadas CE 7 Linhas", ImplementCategory.CULTIVADOR, HitchType.THREE_POINT, 55.0, 3.5, 620.0, 0.0, 18500.0, RED_BALDAN, "Cultivador de entrelinhas com enxadas tipo escarificador leve para capina mecânica sem agrotóxico.", null, "Cultivador"),
    toImp("cultivador_sao_jose_ch_9l", "São José", "Cultivador Hidráulico São José 9L", ImplementCategory.CULTIVADOR, HitchType.THREE_POINT, 75.0, 4.5, 890.0, 0.0, 26000.0, BLUE_SAO_JOSE, "Paralelogramo articulado de alta precisão que segue as ondulações do terreno em milho e soja.", null, "Cultivador"),
    toImp("cultivador_arrasto_multiflex_13l", "São José", "Cultivador de Arrasto Multiflex 13L", ImplementCategory.CULTIVADOR, HitchType.DRAWBAR, 120.0, 6.0, 1750.0, 0.0, 48000.0, BLUE_SAO_JOSE, "Vibro-cultivador de arrasto com dentes flexíveis e rodas calibradoras de profundidade.", null, "Cultivador"),
    toImp("enxada_rotativa_mecrul_erp150", "Mec-Rul", "Enxada Rotativa Mec-Rul ERP 150 (TDP)", ImplementCategory.CULTIVADOR, HitchType.THREE_POINT, 45.0, 1.5, 540.0, 0.0, 22000.0, RED_MEC_RUL, "Enxada rotativa acionada pela TDP (540 RPM) para hortas, canteiros e destorroamento fino.", null, "Enxada rotativa"),
    toImp("enxada_rotativa_mecrul_erp220", "Mec-Rul", "Enxada Rotativa ERP 220 Heavy (TDP)", ImplementCategory.CULTIVADOR, HitchType.THREE_POINT, 80.0, 2.2, 790.0, 0.0, 35000.0, RED_MEC_RUL, "Rotativa pesada para preparo de solo em passada única com grade niveladora traseira.", null, "Enxada rotativa"),
    toImp("sulcador_baldan_sul3", "Baldan", "Sulcador Agrícola SUL-3 Linhas", ImplementCategory.SULCADOR, HitchType.THREE_POINT, 65.0, 2.7, 580.0, 0.0, 19500.0, RED_BALDAN, "Sulcador com asas reguláveis para abertura de sulcos em cana, mandioca, batata e mudas.", null, "Sulcador"),
    toImp("terraceador_arrasto_tac16", "Tatu Marchesan", "Terraceador Agrícola TAC-16 Discos", ImplementCategory.TERRACEADOR, HitchType.DRAWBAR, 140.0, 4.5, 2900.0, 0.0, 62000.0, RED_TATU, "Construção e manutenção de terraços de base larga e curvas de nível para controle de erosão.", null, "Terraceador"),
    toImp("rolo_faca_tombador_rfa30", "Baldan", "Rolo-Faca Agrícola Tombador RFA 3.0m", ImplementCategory.ROLO_FACA, HitchType.DRAWBAR, 70.0, 3.0, 1650.0, 0.0, 28000.0, RED_BALDAN, "Acama e tomba biomassa de adubos verdes (aveia, crotalária) sem picar, protegendo o solo.", null, "Rolo-faca"),
    toImp("destorroador_acoplado_dt36", "São José", "Destorroador Acoplado Rolador DT 3.6m", ImplementCategory.CULTIVADOR, HitchType.DRAWBAR, 50.0, 3.6, 950.0, 0.0, 18000.0, BLUE_SAO_JOSE, "Rolo corrugado com anéis dentados para quebrar torrões na barra traseira de arados e grades.", null, "Destorroador"),

    // ==========================================
    // PARTE 5: ROÇADEIRAS SÃO JOSÉ & TRITON (8)
    // ==========================================
    toImp("rocadeira_sao_jose_rsj150", "São José", "Roçadeira São José RSJ 150 (3 Pontos)", ImplementCategory.ROCADEIRA, HitchType.THREE_POINT, 40.0, 1.5, 420.0, 0.0, 14500.0, BLUE_SAO_JOSE, "Roçadeira hidráulica traseira com giro livre e navalhas duplas temperadas para tratores pequenos e médios.", null, "Roçadeira"),
    toImp("rocadeira_sao_jose_rsj180", "São José", "Roçadeira São José RSJ 180 Super", ImplementCategory.ROCADEIRA, HitchType.THREE_POINT, 55.0, 1.8, 510.0, 0.0, 18200.0, BLUE_SAO_JOSE, "Traseira 3 pontos com patins de regulagem de altura para limpeza de pasto sujo e capoeira.", null, "Roçadeira"),
    toImp("rocadeira_sao_jose_rasj240", "São José", "Roçadeira de Arrasto RASJ 240", ImplementCategory.ROCADEIRA, HitchType.DRAWBAR, 70.0, 2.4, 980.0, 0.0, 27500.0, BLUE_SAO_JOSE, "Roçadeira de arrasto com rodas traseiras de nivelamento e cardan com embreagem de segurança.", null, "Roçadeira"),
    toImp("rocadeira_lateral_sao_jose_rlsj170", "São José", "Roçadeira Lateral Ecológica RLSJ 170", ImplementCategory.ROCADEIRA, HitchType.THREE_POINT, 60.0, 1.7, 680.0, 0.0, 24500.0, BLUE_SAO_JOSE, "Deslocamento hidráulico lateral para roçar embaixo de pomares, cafezais e beiras de cerca.", null, "Roçadeira lateral"),
    toImp("rocadeira_triton_rt160", "Triton", "Roçadeira Triton Traseira RT 160", ImplementCategory.ROCADEIRA, HitchType.THREE_POINT, 45.0, 1.6, 460.0, 0.0, 16800.0, GREEN_TRITON, "Chassi monobloco em aço carbono com caixa multiplicadora reforçada banhada a óleo.", null, "Roçadeira"),
    toImp("rocadeira_triton_rp300_tandem", "Triton", "Roçadeira Triton Pesada Arrasto RP 300", ImplementCategory.ROCADEIRA, HitchType.DRAWBAR, 85.0, 3.0, 1350.0, 0.0, 36000.0, GREEN_TRITON, "Duplo rotor em linha com 4 navalhas para corte pesado de pastagens degradadas e capim alto.", null, "Roçadeira pesada"),
    toImp("triturador_restos_triton_trc250", "Triton", "Triturador de Restos Culturais TRC 250", ImplementCategory.ROCADEIRA, HitchType.THREE_POINT, 90.0, 2.5, 1180.0, 0.0, 39500.0, GREEN_TRITON, "Rotor tubular com martelos oscilantes para triturar restos de milho, algodão e podas de café.", null, "Triturador de restos"),
    toImp("picador_capim_sao_jose_pcf100", "São José", "Picador de Capim e Forragens PCF 100", ImplementCategory.ENSILADEIRA, HitchType.THREE_POINT, 50.0, 1.8, 590.0, 2500.0, 21500.0, BLUE_SAO_JOSE, "Picador acoplado aos 3 pontos com bica giratória para alimentar cochos ou carretas forrageiras.", null, "Picador de capim"),

    // ==========================================
    // PARTE 6: PLATAFORMAS AGRÍCOLAS TRITON (4)
    // ==========================================
    toImp("plat_triton_graos_25ft", "Triton", "Plataforma de Corte Triton Soja/Trigo 25 Pés", ImplementCategory.PLATAFORMA_GRAOS, HitchType.HEADER, 220.0, 7.6, 2600.0, 0.0, 165000.0, GREEN_TRITON, "Plataforma flexível flutuante para grãos finos com caracol de alimentação contínua para colheitadeiras.", null, "Plataforma de grãos"),
    toImp("plat_triton_graos_35ft", "Triton", "Plataforma de Corte Triton Soja/Trigo 35 Pés", ImplementCategory.PLATAFORMA_GRAOS, HitchType.HEADER, 320.0, 10.6, 3900.0, 0.0, 275000.0, GREEN_TRITON, "Plataforma draper de alta capacidade para colheitadeiras axiais de grande porte.", null, "Plataforma de grãos"),
    toImp("plat_triton_milho_8l", "Triton", "Plataforma de Milho Triton ColheMax 8L", ImplementCategory.PLATAFORMA_MILHO, HitchType.HEADER, 180.0, 4.0, 2450.0, 0.0, 142000.0, GREEN_TRITON, "Plataforma de milho de 8 linhas em 45/50 cm com bicos leves em polietileno anti-impacto.", null, "Plataforma de milho"),
    toImp("plat_triton_milho_12l", "Triton", "Plataforma de Milho Triton ColheMax 12L", ImplementCategory.PLATAFORMA_MILHO, HitchType.HEADER, 240.0, 6.0, 3600.0, 0.0, 218000.0, GREEN_TRITON, "12 linhas com despigadores sincronizados e recolhimento limpo mesmo em milho tombado.", null, "Plataforma de milho"),

    // ==========================================
    // PARTE 7: CARREGADORES FRONTAIS & ACESSÓRIOS (9)
    // ==========================================
    toImp("carregador_baldan_pca800", "Baldan", "Carregador Frontal Hidráulico Baldan PCA 800", ImplementCategory.CARREGADOR_FRONTAL, HitchType.FRONT_LOADER, 65.0, 2.0, 780.0, 800.0, 28500.0, RED_BALDAN, "Braço frontal hidráulico com cilindros de dupla ação e engate rápido no trator.", null, "Carregador frontal"),
    toImp("carregador_sao_jose_pam1200", "São José", "Carregador Frontal São José PAM 1200", ImplementCategory.CARREGADOR_FRONTAL, HitchType.FRONT_LOADER, 90.0, 2.2, 980.0, 1200.0, 36000.0, BLUE_SAO_JOSE, "Conjunto frontal reforçado com auto-nivelamento mecânico de concha e joystick.", null, "Carregador frontal"),
    toImp("concha_frontal_terra_08m3", "Baldan", "Concha Frontal para Terra e Cascalho 0.8m³", ImplementCategory.CONCHA_AGRICOLA, HitchType.FRONT_LOADER, 60.0, 1.8, 290.0, 800.0, 9500.0, DARK, "Caçamba dianteira em chapa de alta resistência com dentes de penetração para solo e brita.", null, "Concha frontal"),
    toImp("concha_silagem_alta_15m3", "São José", "Concha de Alta Capacidade para Silagem 1.5m³", ImplementCategory.CONCHA_AGRICOLA, HitchType.FRONT_LOADER, 75.0, 2.2, 360.0, 1500.0, 13500.0, BLUE_SAO_JOSE, "Concha volumosa com perfil aberto para desabastecer silos e carregar forragem.", null, "Concha silagem"),
    toImp("concha_esterco_garra_12m3", "São José", "Concha com Garras para Esterco 1.2m³", ImplementCategory.CONCHA_AGRICOLA, HitchType.FRONT_LOADER, 70.0, 2.0, 420.0, 1200.0, 15800.0, BLUE_SAO_JOSE, "Garras superiores hidráulicas para limpeza rápida de currais, estábulos e camas de frango.", null, "Concha esterco"),
    toImp("garfo_pallets_agro_1500kg", "Baldan", "Garfo Frontal para Pallets Agro 1500 kg", ImplementCategory.GARFO_PALLET_FARDO, HitchType.FRONT_LOADER, 55.0, 1.2, 180.0, 0.0, 7800.0, DARK, "Pontas forjadas reguláveis para movimentação de pallets de sementes, fertilizantes e sacarias.", null, "Garfo de pallet"),
    toImp("garfo_lanca_fardos_duplo", "São José", "Garfo e Lança para Fardos Duplo", ImplementCategory.GARFO_PALLET_FARDO, HitchType.FRONT_LOADER, 50.0, 1.6, 140.0, 0.0, 6400.0, BLUE_SAO_JOSE, "Lanças cônicas em aço temperado para transportar e empilhar fardos redondos e prismáticos de feno.", null, "Lança fardos"),
    toImp("lamina_frontal_plaina_24m", "Baldan", "Lâmina Frontal Agrícola (Plaina) 2.4m", ImplementCategory.LAMINA_FRONTAL, HitchType.FRONT_LOADER, 75.0, 2.4, 460.0, 0.0, 16500.0, RED_BALDAN, "Lâmina dianteira com angulação hidráulica para terraplanagem leve, estradas e terreiros.", null, "Lâmina frontal"),
    toImp("empurrador_silagem_frontal_26m", "São José", "Empurrador Frontal de Silagem 2.6m", ImplementCategory.LAMINA_FRONTAL, HitchType.FRONT_LOADER, 80.0, 2.6, 510.0, 0.0, 17800.0, BLUE_SAO_JOSE, "Painel frontal reforçado para espalhar e compactar silagem de milho em silos trincheira.", null, "Empurrador silagem"),

    // ==========================================
    // PARTE 8: PULVERIZADORES & ADUBAÇÃO (7)
    // ==========================================
    toImp("pulv_jacto_condor_800_am14", "Jacto", "Pulverizador Condor 800 AM14 (3 Pontos)", ImplementCategory.PULVERIZADOR, HitchType.THREE_POINT, 55.0, 14.0, 680.0, 800.0, 48000.0, ORANGE_JACTO, "Pulverizador traseiro 3 pontos com barras pantográficas de 14m e comando master.", null, "Pulverizador 3 pontos"),
    toImp("pulv_triton_pulvemax_600", "Triton", "Pulverizador Triton PulveMax 600 Barra 12m", ImplementCategory.PULVERIZADOR, HitchType.THREE_POINT, 45.0, 12.0, 520.0, 600.0, 36000.0, GREEN_TRITON, "Pulverizador leve com barras articuladas manuais para sítios, hortas e pomares.", null, "Pulverizador 3 pontos"),
    toImp("pulv_sao_jose_pulver_2000", "São José", "Pulverizador de Arrasto São José 2000L", ImplementCategory.PULVERIZADOR, HitchType.DRAWBAR, 75.0, 18.0, 1850.0, 2000.0, 84000.0, BLUE_SAO_JOSE, "Pulverizador de arrasto com suspensão trapezoidal, tanque em fibra e barras de 18 metros.", null, "Pulverizador de arrasto"),
    toImp("pulv_jacto_columbia_cross3000", "Jacto", "Pulverizador Jacto Columbia Cross 3000L", ImplementCategory.PULVERIZADOR, HitchType.DRAWBAR, 95.0, 24.0, 2650.0, 3000.0, 145000.0, ORANGE_JACTO, "Barras hidráulicas de 24 metros, corte eletrônico de seções e incorporador de calda.", null, "Pulverizador de arrasto"),
    toImp("pulv_triton_force_4000", "Triton", "Pulverizador Triton Force 4000L Barra 30m", ImplementCategory.PULVERIZADOR, HitchType.DRAWBAR, 125.0, 30.0, 3450.0, 4000.0, 215000.0, GREEN_TRITON, "Grande capacidade operacional com barras híbridas de 30m e suspensão ativa a ar.", null, "Pulverizador de arrasto"),
    toImp("dist_calcario_adubo_sao_jose_5000", "São José", "Distribuidor de Calcário DCSA 5000", ImplementCategory.DISTRIBUIDOR, HitchType.DRAWBAR, 80.0, 10.0, 2100.0, 5000.0, 62000.0, BLUE_SAO_JOSE, "Distribuidor por esteira de borracha larga para calcário seco/úmido, gesso e adubo orgânico.", null, "Distribuidor de calcário"),
    toImp("dist_lanco_baldan_dca2500", "Baldan", "Distribuidor Centrífugo a Lanço DCA 2500", ImplementCategory.DISTRIBUIDOR, HitchType.THREE_POINT, 60.0, 16.0, 780.0, 2500.0, 32000.0, RED_BALDAN, "Duplo disco centrífugo em aço inoxidável para aplicação uniforme de sementes e adubo NPK.", null, "Distribuidor a lanço"),
)

val IMPLEMENT_EXPANSION_CATALOG: List<ImplementDef> =
    EXTRA_IMPLEMENTS_ROWS + EXTRA_TRAILERS_ROWS + NEW_AGRITECH_IMPLEMENTS_24 + BRAZILIAN_RURAL_EXPANSION_CATALOG
