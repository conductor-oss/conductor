# Conductor OSS — File Management Use Cases

Five real-world scenarios where Conductor orchestrates file creation, processing, and delivery across workflow stages.

---

## 1. Returns & Refund Document Processing

A customer initiates a product return. Conductor orchestrates the intake of return photos/documents, validates eligibility, generates an RMA (Return Merchandise Authorization) form, and produces the final refund receipt — all as a single traceable workflow.

### Workflow

```mermaid
flowchart TD
    A["Customer Submits<br/>Return Request"] --> B["HTTP Task:<br/>Fetch Order Details"]
    B --> C["INLINE Task:<br/>Validate Return Window"]
    C --> D{"SWITCH:<br/>Eligible?"}
    D -- No --> E["Generate Denial<br/>Letter PDF"]
    E --> E1["Email Denial<br/>to Customer"]
    D -- Yes --> F["HUMAN Task:<br/>Agent Reviews Photos"]
    F --> G{"SWITCH:<br/>Condition Check"}
    G -- Damaged --> H["Generate RMA Form<br/>+ Prepaid Shipping Label"]
    G -- Wrong Item --> H
    G -- Other --> I["HUMAN Task:<br/>Escalate to Supervisor"]
    I --> H
    H --> J["FORK"]
    J --> K["Branch 1:<br/>Process Refund via<br/>Payment Gateway"]
    J --> L["Branch 2:<br/>Generate Refund<br/>Receipt PDF"]
    J --> M["Branch 3:<br/>Update Inventory<br/>System"]
    K --> N["JOIN"]
    L --> N
    M --> N
    N --> O["Email RMA + Receipt<br/>+ Shipping Label<br/>to Customer"]
    O --> P["Archive All Docs<br/>to S3"]

    style A fill:#4CAF50,color:#fff
    style D fill:#FF9800,color:#fff
    style G fill:#FF9800,color:#fff
    style J fill:#2196F3,color:#fff
    style N fill:#2196F3,color:#fff
    style P fill:#9C27B0,color:#fff
```

### Files Produced

| Stage | File | Format |
|-------|------|--------|
| RMA Generation | `rma_RET-9001.pdf` | PDF |
| Shipping Label | `label_RET-9001.png` | 4×6 ZPL/PNG |
| Refund Receipt | `receipt_RET-9001.pdf` | PDF |
| Denial Letter | `denial_RET-9001.pdf` | PDF (if ineligible) |

### Conductor Primitives

SWITCH, HUMAN, FORK/JOIN, HTTP, INLINE, SUB_WORKFLOW

---

## 2. AI-Powered Knowledge Base Builder (RAG Pipeline)

An organization ingests documents (PDFs, Word files, web pages) into an AI-ready knowledge base. Conductor orchestrates crawling, extraction, chunking, embedding generation, and vector store indexing — enabling retrieval-augmented generation (RAG) for chatbots and search.

### Workflow

```mermaid
flowchart TD
    A["Trigger:<br/>New Docs Uploaded<br/>to S3 Bucket"] --> B["DO_WHILE:<br/>Process Each Document"]
    B --> C{"SWITCH:<br/>File Type?"}
    C -- PDF --> D["Extract Text<br/>via Apache Tika"]
    C -- DOCX --> E["Extract Text<br/>via python-docx"]
    C -- HTML --> F["Scrape & Clean<br/>via BeautifulSoup"]
    C -- Other --> G["OCR via<br/>Tesseract"]
    D --> H["INLINE Task:<br/>Chunk Text<br/>(512 tokens, 50 overlap)"]
    E --> H
    F --> H
    G --> H
    H --> I["DYNAMIC_FORK:<br/>Generate Embeddings<br/>(1 per chunk)"]
    I --> J["LLM_TEXT_COMPLETE:<br/>Create Embedding Vector"]
    J --> K["JOIN:<br/>Collect All Vectors"]
    K --> L["HTTP Task:<br/>Upsert to Vector DB<br/>(Pinecone / Weaviate)"]
    L --> M["Generate Metadata<br/>Index JSON"]
    M --> N{"More Docs?"}
    N -- Yes --> B
    N -- No --> O["Write Master<br/>Index Manifest"]
    O --> P["Upload Manifest<br/>+ Logs to S3"]

    style A fill:#4CAF50,color:#fff
    style C fill:#FF9800,color:#fff
    style I fill:#2196F3,color:#fff
    style K fill:#2196F3,color:#fff
    style N fill:#FF9800,color:#fff
    style P fill:#9C27B0,color:#fff
```

### Files Produced

| Stage | File | Format |
|-------|------|--------|
| Extracted Text | `extracted_{doc_id}.txt` | Plain text |
| Chunk Manifest | `chunks_{doc_id}.jsonl` | JSONL |
| Embedding Vectors | `embeddings_{doc_id}.npy` | NumPy binary |
| Metadata Index | `index_{doc_id}.json` | JSON |
| Master Manifest | `kb_manifest_{run_id}.json` | JSON |
| Pipeline Log | `pipeline_log_{run_id}.txt` | Text |

### Conductor Primitives

DO_WHILE, SWITCH, DYNAMIC_FORK, LLM_TEXT_COMPLETE, HTTP, INLINE

---

## 3. Multi-Format Media Transcoding & Publishing

A media company uploads a master video file. Conductor fans out transcoding jobs to produce multiple resolutions and formats, generates thumbnails, extracts subtitles via speech-to-text, and publishes everything to a CDN — all in parallel where possible.

### Workflow

```mermaid
flowchart TD
    A["Master Video<br/>Uploaded (4K ProRes)"] --> B["INLINE Task:<br/>Validate & Extract<br/>Media Metadata"]
    B --> C["FORK (3 Branches)"]
    
    C --> D["Branch 1:<br/>DYNAMIC_FORK<br/>Transcode Variants"]
    D --> D1["1080p H.264 MP4"]
    D --> D2["720p H.264 MP4"]
    D --> D3["480p H.264 MP4"]
    D --> D4["1080p WebM VP9"]
    D --> D5["HLS Adaptive<br/>Playlist (.m3u8)"]
    
    C --> E["Branch 2:<br/>Thumbnail Generation"]
    E --> E1["Extract Keyframes<br/>(every 30s)"]
    E1 --> E2["Resize to<br/>320×180 JPG"]
    E2 --> E3["Generate Poster<br/>Image 1920×1080"]
    
    C --> F["Branch 3:<br/>Speech-to-Text"]
    F --> F1["LLM_TEXT_COMPLETE:<br/>Transcribe Audio"]
    F1 --> F2["Generate SRT<br/>Subtitle File"]
    F2 --> F3["Generate VTT<br/>Subtitle File"]

    D1 --> G["JOIN"]
    D2 --> G
    D3 --> G
    D4 --> G
    D5 --> G
    E3 --> G
    F3 --> G
    
    G --> H["Generate<br/>Manifest JSON"]
    H --> I["HTTP Task:<br/>Upload All Assets<br/>to CDN"]
    I --> J["HTTP Task:<br/>Update CMS<br/>with URLs"]
    J --> K["Notify Editorial<br/>Team via Slack"]

    style A fill:#4CAF50,color:#fff
    style C fill:#2196F3,color:#fff
    style D fill:#FF5722,color:#fff
    style G fill:#2196F3,color:#fff
    style K fill:#9C27B0,color:#fff
```

### Files Produced

| Stage | File | Format |
|-------|------|--------|
| Transcoded Videos | `video_{res}.mp4`, `video_1080p.webm` | MP4, WebM |
| HLS Playlist | `stream.m3u8` + segment `.ts` files | HLS |
| Thumbnails | `thumb_{timestamp}.jpg` | JPEG |
| Poster Image | `poster.jpg` | JPEG 1920×1080 |
| Subtitles | `subs_en.srt`, `subs_en.vtt` | SRT, VTT |
| Manifest | `publish_manifest.json` | JSON |

### Conductor Primitives

FORK/JOIN, DYNAMIC_FORK, LLM_TEXT_COMPLETE, HTTP, INLINE

---

## 4. Order Invoice, Packing Slip & Shipping Label Generation

An e-commerce order triggers Conductor to fetch order data, then fan out in parallel to generate three documents — a customer-facing invoice, a warehouse packing slip (no pricing), and a carrier shipping label — before bundling and distributing them.

### Workflow

```mermaid
flowchart TD
    A["Order Placed<br/>(Webhook)"] --> B["HTTP Task:<br/>Fetch Order +<br/>Customer Profile"]
    B --> C["INLINE Task:<br/>Calculate Totals<br/>(tax, discounts, shipping)"]
    C --> D["FORK (3 Branches)"]
    
    D --> E["Branch 1:<br/>Generate Invoice PDF"]
    E --> E1["Apply Branding<br/>(logo, colors, footer)"]
    E1 --> E2["Format Line Items<br/>+ Tax Breakdown"]
    E2 --> E3["Render PDF<br/>invoice_ORD-12345.pdf"]
    
    D --> F["Branch 2:<br/>Generate Packing Slip"]
    F --> F1["Strip Pricing Info"]
    F1 --> F2["Add Pick Locations<br/>+ Bin Numbers"]
    F2 --> F3["Add Warehouse<br/>Barcode"]
    F3 --> F4["Render PDF<br/>packslip_ORD-12345.pdf"]
    
    D --> G["Branch 3:<br/>Generate Shipping Label"]
    G --> G1{"SWITCH:<br/>Carrier?"}
    G1 -- FedEx --> G2["Call FedEx API"]
    G1 -- UPS --> G3["Call UPS API"]
    G1 -- USPS --> G4["Call USPS API"]
    G2 --> G5["Receive Tracking #<br/>+ Label Image"]
    G3 --> G5
    G4 --> G5
    G5 --> G6["Render Label<br/>label_ORD-12345.png"]
    
    E3 --> H["JOIN"]
    F4 --> H
    G6 --> H
    
    H --> I["Bundle 3 Files<br/>into Order Package"]
    I --> J["Upload to S3<br/>orders/ORD-12345/"]
    J --> K["FORK (2 Branches)"]
    K --> L["Email Invoice<br/>to Customer"]
    K --> M["Send Slip + Label<br/>to Warehouse Printer"]
    L --> N["JOIN"]
    M --> N
    N --> O["Update Order Status:<br/>Ready to Ship"]

    style A fill:#4CAF50,color:#fff
    style D fill:#2196F3,color:#fff
    style G1 fill:#FF9800,color:#fff
    style H fill:#2196F3,color:#fff
    style K fill:#2196F3,color:#fff
    style N fill:#2196F3,color:#fff
    style O fill:#9C27B0,color:#fff
```

### Files Produced

| Stage | File | Format |
|-------|------|--------|
| Invoice | `invoice_ORD-12345.pdf` | PDF |
| Packing Slip | `packslip_ORD-12345.pdf` | PDF |
| Shipping Label | `label_ORD-12345.png` | 4×6 ZPL/PNG |

### Conductor Primitives

FORK/JOIN, SWITCH, HTTP, INLINE, SUB_WORKFLOW

---

## 5. Enterprise Video Surveillance Archival & Alert Pipeline

A network of security cameras streams footage to edge servers. Conductor orchestrates the pipeline: ingest video segments, run AI-based anomaly detection, generate alert clips with annotations, archive raw footage with retention policies, and produce daily summary reports.

### Workflow

```mermaid
flowchart TD
    A["Camera Feed:<br/>60s Segment Arrives<br/>on Edge Server"] --> B["INLINE Task:<br/>Extract Metadata<br/>(camera ID, timestamp,<br/>resolution)"]
    B --> C["Upload Raw Segment<br/>to Cold Storage<br/>(S3 Glacier)"]
    C --> D["HTTP Task:<br/>AI Anomaly Detection<br/>Model Inference"]
    D --> E{"SWITCH:<br/>Anomaly Detected?"}
    
    E -- No --> F["Log: Normal<br/>Update Daily Counter"]
    
    E -- Yes --> G["FORK (3 Branches)"]
    G --> H["Branch 1:<br/>Clip 30s Around<br/>Anomaly Timestamp"]
    H --> H1["Overlay Bounding<br/>Boxes + Labels"]
    H1 --> H2["Render Alert Clip<br/>alert_CAM04_1712345678.mp4"]
    
    G --> I["Branch 2:<br/>Generate Alert<br/>Snapshot"]
    I --> I1["Extract Best Frame"]
    I1 --> I2["Annotate with<br/>Detection Metadata"]
    I2 --> I3["Save Snapshot<br/>alert_CAM04_1712345678.jpg"]
    
    G --> J["Branch 3:<br/>Create Incident<br/>Report"]
    J --> J1["LLM_TEXT_COMPLETE:<br/>Summarize Event"]
    J1 --> J2["Generate PDF<br/>incident_1712345678.pdf"]
    
    H2 --> K["JOIN"]
    I3 --> K
    J2 --> K
    
    K --> L["Upload Alert Bundle<br/>to Hot Storage (S3)"]
    L --> M["HTTP Task:<br/>Push Notification<br/>to Security Team"]
    M --> N["Log Incident<br/>to SIEM"]

    F --> O["TIMER:<br/>End of Day?"]
    N --> O
    O --> P["DO_WHILE:<br/>Aggregate All<br/>Camera Logs"]
    P --> Q["Generate Daily<br/>Summary Report PDF"]
    Q --> R["Apply Retention<br/>Policy (90-day<br/>hot → cold → delete)"]
    R --> S["Email Daily Report<br/>to Facility Manager"]

    style A fill:#4CAF50,color:#fff
    style E fill:#FF9800,color:#fff
    style G fill:#2196F3,color:#fff
    style K fill:#2196F3,color:#fff
    style O fill:#FF5722,color:#fff
    style S fill:#9C27B0,color:#fff
```

### Files Produced

| Stage | File | Format |
|-------|------|--------|
| Raw Segment | `raw_CAM04_1712345678.mp4` | MP4 (60s) |
| Alert Clip | `alert_CAM04_1712345678.mp4` | MP4 (30s, annotated) |
| Alert Snapshot | `alert_CAM04_1712345678.jpg` | JPEG (annotated) |
| Incident Report | `incident_1712345678.pdf` | PDF |
| Daily Summary | `daily_report_2026-04-08.pdf` | PDF |

### Conductor Primitives

FORK/JOIN, SWITCH, DO_WHILE, TIMER, LLM_TEXT_COMPLETE, HTTP, INLINE

---

*Generated for Conductor OSS file management use case exploration.*
