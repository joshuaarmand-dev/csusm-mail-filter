# CSUSM Mail – Gmail Security Filter

This project is a Java desktop app.  
It connects to Gmail, reads recent inbox messages, and marks them as:

- **Filtered** – trusted emails
- **Unfiltered** – other normal emails
- **Unsafe** – emails with risky links or blocked domains

The project was built for **SE 370** at **California State University San Marcos**.

---

## Motivation

Students and staff at CSUSM receive many emails from unknown senders.  
Some messages are phishing or scams.  
Built-in spam filters often hide their rules.  
Users may not know *why* a message looks unsafe.

This app aims to:

- Show which emails may be unsafe.  
- Explain why an email looks unsafe.  
- Let the user grow a personal **Allow** and **Block** list of domains.

The first design used Microsoft Graph and Outlook.  
Campus permissions blocked the required access.  
We then switched to the **Gmail API** and kept the same idea.

---

## Features

- Java Swing desktop window with three tabs:
  - **Filtered** – from CSUSM or domains in the Allow list  
  - **Unfiltered** – normal emails with no clear risk  
  - **Unsafe** – emails that match one or more risk rules
- Connects to Gmail with **OAuth 2.0**.  
  Passwords never pass through the app.
- Scans the latest *N* inbox messages.  
  The user chooses *N*.
- Parses HTML email bodies with **Jsoup** and extracts links.
- Flags possible risk:
  - URL redirectors (for example `bit.ly`, `t.co`).
  - Certain top-level domains (for example `.xyz`, `.top`, `.ru`).
  - Sender domains or link hosts in the Block list.
- Stores **Allow** and **Block** domain sets in a local JSON file.
- Opens the selected message in Gmail in the web browser.

---

## Tech Stack

- **Language:** Java 17  
- **Build tool:** Maven  
- **UI:** Swing (`JFrame`, `JTable`, `JTabbedPane`)  
- **APIs:** Gmail API, Google OAuth 2.0 (installed application)  
- **Libraries:**
  - `google-api-client`
  - `google-oauth-client-jetty`
  - `google-api-services-gmail`
  - `jsoup` for HTML parsing
  - `gson` for JSON storage

---

## Architecture Overview

Main parts of the code:

- `CsusmMailUI`  
  - Sets up the Swing window.  
  - Handles clicks on **Scan**, **Open**, **Allow**, and **Block**.  
  - Calls Gmail, runs analysis, and updates table models.

- `Msg`  
  - Holds one email row:  
    `id`, `subject`, `from`, `received`, `webLink`, `reasons`, and flags.

- `Analysis`  
  - Holds three lists of `Msg`: `filtered`, `unfiltered`, and `unsafe`.

- `MailTableModel`  
  - Extends `AbstractTableModel`.  
  - Exposes a `List<Msg>` to a `JTable`.

- `CompaniesStore`  
  - Manages two sets: `allow` and `block`.  
  - Loads and saves them from a local `companies.json` file.

- `LinkScanResult`  
  - Holds `suspicious` (boolean) and a list of reason strings.

The app is a **desktop client only**.  
It does not have a backend server.  
All analysis runs on the user’s machine.

---

## Prerequisites

To build and run the app you need:

- Java **17** JDK  
- Maven **3.x**  
- A Google account  
- A Google Cloud project with:
  - **Gmail API** enabled  
  - An **OAuth 2.0 Client ID** of type **Desktop app**

---

## Setup

1. In Google Cloud Console:
   - Create an OAuth client of type **Desktop app**.  
   - Download the client secrets JSON file.

2. Rename that file to:

   ```text
   credentials.json
