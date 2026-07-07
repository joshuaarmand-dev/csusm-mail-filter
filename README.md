# CSUSM Mail Filter

A Java desktop application that connects to Gmail and helps users identify potentially unsafe emails through local, rule-based analysis.

The app scans recent inbox messages, extracts links from email content, checks sender and link domains against user-managed allow/block lists, and organizes messages into **Filtered**, **Unfiltered**, or **Unsafe** categories.

> Originally built for SE 370 at California State University San Marcos.

---

## Overview

CSUSM Mail Filter is a desktop Gmail security assistant designed to make email risk analysis more transparent.

Instead of silently hiding or flagging messages, the app shows users why an email may be suspicious. It can flag messages that contain risky links, known redirectors, suspicious top-level domains, or sender/link domains that appear in the user’s local block list.

The application runs locally on the user’s machine and does not use a backend server.

---

## Message Categories

The app classifies scanned emails into three groups:

* **Filtered** — trusted messages from CSUSM or domains in the user’s allow list
* **Unfiltered** — normal messages with no clear risk indicators
* **Unsafe** — messages that match one or more risk rules, such as suspicious links or blocked domains

---

## Motivation

Students and staff at CSUSM receive many emails from unknown senders. Some messages may be phishing attempts, scams, or suspicious external communications.

Built-in spam filters can help, but they often do not explain why a message was considered risky. This project was built to give users a clearer, more transparent way to review recent Gmail messages and understand potential warning signs.

The first design used Microsoft Graph and Outlook, but campus permissions blocked the required access. The project was then redesigned to use the Gmail API while preserving the original goal: helping users identify risky emails more clearly.

---

## Features

* Java Swing desktop interface with three message tabs:

    * **Filtered**
    * **Unfiltered**
    * **Unsafe**
* Secure Gmail login using Google OAuth 2.0
* Scans the latest user-selected number of inbox messages
* Parses HTML email bodies with Jsoup
* Extracts and analyzes links from email content
* Flags possible email risks, including:

    * URL redirectors such as `bit.ly` and `t.co`
    * Suspicious top-level domains such as `.xyz`, `.top`, and `.ru`
    * Sender domains found in the local block list
    * Link hosts found in the local block list
* Supports user-managed allow and block domain lists
* Stores allow/block lists locally in a JSON file
* Opens selected messages directly in Gmail through the browser

---

## Privacy and Security

* Gmail authentication is handled through Google OAuth 2.0
* The user’s Gmail password never passes through the app
* Email analysis runs locally on the user’s machine
* The app does not send email content to a backend server
* Allow and block lists are stored locally in `companies.json`
* This app is a rule-based review tool and should not be treated as a complete replacement for Gmail’s built-in security protections

---

## Tech Stack

* **Language:** Java 17
* **Build Tool:** Maven
* **UI:** Java Swing
* **APIs:** Gmail API, Google OAuth 2.0
* **Libraries:**

    * `google-api-client`
    * `google-oauth-client-jetty`
    * `google-api-services-gmail`
    * `jsoup`
    * `gson`

---

## Architecture Overview

The application is currently organized around the following main components:

### `CsusmMailUI`

Handles the Swing desktop interface, including the main window, message tables, tabs, and user actions such as scanning, opening, allowing, and blocking messages.

### `Msg`

Represents one email message displayed in the app. It stores fields such as message ID, subject, sender, received date, Gmail web link, risk reasons, and classification flags.

### `Analysis`

Stores the results of a scan by separating messages into three lists:

* `filtered`
* `unfiltered`
* `unsafe`

### `MailTableModel`

Extends `AbstractTableModel` and connects a list of `Msg` objects to a Swing `JTable`.

### `CompaniesStore`

Manages the user’s local allow and block domain lists. It loads and saves domain data using a local `companies.json` file.

### `LinkScanResult`

Stores the result of link analysis, including whether a link is suspicious and the reasons it was flagged.

---

## How It Works

1. The user signs in with Gmail through Google OAuth 2.0.
2. The app asks how many recent inbox messages to scan.
3. Gmail message metadata and email bodies are retrieved through the Gmail API.
4. HTML email content is parsed with Jsoup.
5. Sender domains and extracted links are checked against rule-based risk indicators.
6. Messages are grouped into Filtered, Unfiltered, or Unsafe tabs.
7. The user can open messages in Gmail or update local allow/block lists.

---

## Prerequisites

To build and run the app, you need:

* Java 17 JDK
* Maven 3.x
* A Google account
* A Google Cloud project with:

    * Gmail API enabled
    * An OAuth 2.0 Client ID of type **Desktop app**

---

## Setup

1. In Google Cloud Console, create an OAuth client of type **Desktop app**.

2. Download the client secrets JSON file.

3. Rename the file to:

   ```text
   credentials.json
   ```

4. Place `credentials.json` in the project location expected by the app.

5. Build the project with Maven:

   ```bash
   mvn clean package
   ```

6. Run the application:

   ```bash
   mvn exec:java
   ```

---

## Current Limitations

* Detection is rule-based and may not catch every phishing attempt
* Some safe messages may be flagged depending on the user’s allow/block settings
* The app currently focuses on Gmail inbox messages
* The current version is a desktop client only and does not include a backend service
* The UI is built with Swing and may be modernized in a future version

---

## Roadmap

Planned improvements include:

* Refactor code into separate UI, service, model, and utility classes
* Add unit tests for email and link analysis logic
* Add GitHub Actions for automated builds
* Add screenshots and demo GIFs to the README
* Add a detailed risk explanation panel for unsafe messages
* Add CSV export for scan results
* Improve link analysis and domain classification rules
* Explore a future JavaFX interface redesign

---

## Disclaimer

This project is intended as an educational and transparency-focused email review tool. It should not be used as the only form of protection against phishing, scams, or malicious emails.
