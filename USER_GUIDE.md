# Himarket User Guide

This guide is designed for administrators and developers of the Himarket AI Open Platform, providing detailed operational instructions and best practices.

If you are an **Administrator**, this guide will walk you through the complete process from importing gateway instances, creating portals, to publishing API products. If you are a **Developer**, this guide will help you understand how to register an account, subscribe to API products, obtain credentials, and make actual API calls.

Through this guide, you can quickly master the core features of Himarket and achieve efficient management and convenient usage of AI capabilities.

## Table of Contents

- [Himarket Admin Console](#himarket-admin-console)
  - [Register Administrator](#register-administrator)
  - [Import Higress Instance](#import-higress-instance)
  - [Create Portal](#create-portal)
  - [Create API Product](#create-api-product)
  - [Link API](#link-api)
  - [Write Usage Guide](#write-usage-guide)
  - [Publish to Portal](#publish-to-portal)
- [Himarket Developer Portal](#himarket-developer-portal)
  - [Access Portal](#access-portal)
  - [Register as Developer](#register-as-developer)
  - [Browse and Subscribe API Products](#browse-and-subscribe-api-products)
  - [Create Consumer](#create-consumer)
  - [AI Playground](#ai-playground)

---

## Himarket Admin Console

The Himarket Admin Console is designed for administrators and operators to manage AI resources, create portals, and publish API products.

### Register Administrator

When accessing the Himarket Admin Console for the first time, you need to register an administrator account.

**Steps:**

1. Access the admin console: `http://localhost:5174` (local development environment example)
2. Click the "Register" button
3. Fill in administrator information:
   - Username
   - Password
4. Click "Initialize" to complete account creation
5. Login with the registered account

![](https://github.com/user-attachments/assets/2caca9f6-5da4-4996-8953-ed6f4647cd12)

---

### Import Higress Instance

Before creating API Products, you need to import a Higress gateway instance as the underlying gateway for AI resources.

**Steps:**

1. Login to the admin console
2. Navigate to 【Instance Management】→【Gateway Instance】
3. Click the 【Import Gateway Instance】button
4. Select 【Higress Gateway】
5. Fill in Higress instance information:
   - Console Address: Higress console address (e.g., `http://demo.higress.io`)
   - Username: Higress console username (e.g., `admin`)
   - Password: Higress console password (e.g., `admin`)
   - Gateway Address: Higress gateway service address (e.g., `http://gateway.higress.io`). MCP Server and Model use domain names by default; if not configured, the Gateway address is used
6. Click 【Import】

![](https://github.com/user-attachments/assets/fca47d51-db11-457c-9f06-91342bb1fdfa)

**Notes:**
- Supports importing multiple Higress instances
- After import, view and manage instances in the instance list
  
![](https://github.com/user-attachments/assets/61dda900-6f5f-419b-8b61-34a6fd1da514)

---

### Create Portal

A Portal is a site for external developers. Administrators can create multiple portals, each with independent configurations and API products.

**Steps:**

1. Navigate to the 【Portal】menu
2. Click the 【Create Portal】button
3. Fill in basic portal information:
   - Portal Name: e.g., `himarket-demo`
   - Portal Description: Brief description of the portal's purpose
4. Click 【Confirm】

![](https://github.com/user-attachments/assets/178d4644-eee2-488e-9cac-f76a7cad9a7f)

**Portal Configuration:**

After creating a portal, click on the portal card to enter the detailed configuration page, which includes the following features:

#### 1. Overview

- Portal basic information: name, description, login method, approval policy, etc.
- Statistics:
    - Number of published API products
    - Number of registered developers

#### 2. Products (Product Management)

Manage API products in the current portal:
- View published products
- Check product details
- Unpublish or update products

#### 3. Developers (Developer Management)

Manage developers registered in this portal:
- View developer list
- Approve developer registration requests
- Manage developers' associated Consumers
- View developer subscription status

#### 4. Security (Security Settings)

User Login Configuration:
- Username/Password Login: Traditional username/password method (enabled by default)
- OIDC Login: Supports third-party identity authentication (such as enterprise SSO)

Approval Process Configuration:
- Developer Registration Approval: Choose whether to automatically approve developer registration requests
    - Auto-approve: Developer registration takes effect immediately
    - Manual approval: Requires administrator approval in the backend
- API Product Subscription Approval: Choose whether to automatically approve subscription requests
    - Auto-approve: Subscription takes effect immediately after developer applies
    - Manual approval: Requires administrator approval before API Product can be called

#### 5. Domain (Domain Management)

Configure access domains for the portal:

**Default Domain:**
- System automatically assigns a unique default domain for each portal
- Format: `portal-{id}.api.portal.local`
- Example: `portal-68ac4564bdb292ee9261ff4a.api.portal.local`

**Custom Domain:**
- Supports binding one or more custom domains
- Example: Production environment binds `api.company.com`, local development binds `localhost`

**Notes:**
- Domains need to be configured with DNS resolution or hosts file, pointing to the himarket-frontend service
- Portals use a multi-tenant design, identifying different portal instances through domains
- For local development, it's recommended to bind the `localhost` domain for easy testing
- For production environments, it's recommended to configure real domains and DNS resolution

---

### Create API Product

API Product is the core concept of Himarket. It encapsulates underlying AI capabilities (such as MCP Server, Model, Agent) into standardized API products.

**Steps:**

1. Navigate to the 【API Products】menu
2. Click the 【Create API Product】button
3. Fill in product information:
   - Product Name: e.g., `demo-api`
   - Product Type: Select product type (MCP Server, Model API, Agent API, etc.)
   - Product Description: Brief description of product features and use cases
   - Product Category: Select product category, categories can be used for classification and filtering, supports custom categories
   - Product Icon: Select product icon, can use URL link or upload local file
4. **Special Note**: Model API type products can configure model parameters such as Model name, Max Tokens, Temperature, whether to support web search, etc. Model parameters are used when debugging models in the Himarket frontend. Missing parameters may cause model invocation failures
5. Click 【Confirm】

![](https://github.com/user-attachments/assets/59f94af9-c28d-4313-b17a-79319bbeda8f)

6. After successful creation, enter the product details page

![](https://github.com/user-attachments/assets/f10eb08d-3e4e-4c4f-ae8f-e9af04cf1cdf)

**Product Status:**

Newly created API Products have an initial status of **"Pending Configuration"**. The following operations need to be completed before publishing:

1. **Link API**: Associate the product with actual API resources
2. **Configure Usage Guide**: Write product documentation
3. **Publish to Portal**: Publish the product to a specified portal for developer subscription

---

### Link API

After creating an API Product, you need to link it with actual API resources to establish the mapping between the product and underlying services.

**Steps:**

1. Enter the API Product details page
2. Click the 【Link API】tab
3. Select API resource type:
   - MCP Server: MCP service from Higress or AI Gateway (Enterprise Higress)
   - Model API: Model service from Higress or AI Gateway
   - Agent API: Agent application from AI Gateway
4. Select the API resource to link from the list. Taking Model API as an example, link the AI route on Higress
5. Click 【Link】

![](https://github.com/user-attachments/assets/e5bc4963-c7b9-4bbd-9a0e-55eb06cc91c5)

6. After successful linking, the API Product status changes to **"Pending Publish"**. At this point, the API Product can be published to the Portal. The Link API tab will display API configuration information

![](https://github.com/user-attachments/assets/845935ee-79c4-45dc-9cd0-e49ab840a505)

---

### Write Usage Guide

Write detailed usage guides for API Products to help developers quickly understand and use the APIs.

**Steps:**

1. Enter the API Product details page
2. Click the 【Usage Guide】tab
3. Use the Markdown editor to write documentation:
   - API Product Introduction: Product features and use cases
   - Authentication Method: How to obtain and use credentials
   - Request Examples: Provide complete request example code
   - Response Examples: Show API return data format
   - Error Code Description: List common error codes and handling methods
   - Best Practices: Usage recommendations and precautions
4. Supports real-time preview to ensure correct formatting
5. Click 【Save】

![](https://github.com/user-attachments/assets/5a35a3ce-4dd3-4644-a7b7-f4ba64c5a387)

---

### Publish to Portal

When the API Product configuration is complete, it can be published to a specified portal for developer use.

**Steps:**

1. Enter the API Product details page
2. Confirm the product status is **"Pending Publish"** (API linking completed)
3. Click the 【Publish to Portal】button
4. Select target portal:
   - Select one or more portals from the portal list
   - Can publish to multiple portals simultaneously
5. Click 【Publish】

![](https://github.com/user-attachments/assets/6f09d6aa-08f0-4eb9-aab1-1295236ba986)

**After Publishing:**
- Product will be displayed in the portal immediately
- Developers can browse product details and usage guides
- Developers can apply for product subscription
- Administrators can view subscription and call statistics in the backend

![](https://github.com/user-attachments/assets/dd43e7f5-d490-45bc-a3b1-1ebdb0df66eb)

At this point, the complete process from creating to publishing an API Product is finished!

---

## Himarket Developer Portal

The Developer Portal is a self-service platform for external developers, where they can register, browse API products, apply for subscriptions, and make API calls.

### Access Portal

Himarket portals use a **multi-tenant design**, identifying different portal instances through domains.

**Access Methods:**

1. **Using Default Domain**:
   - Each portal is automatically assigned a default domain
   - Example: `portal-68ac4564bdb292ee9261ff4a.api.portal.local`
   - Requires DNS resolution or hosts file configuration, pointing the domain to the himarket-frontend service

2. **Using Custom Domain**:
   - Custom domains bound by administrators in the backend
   - Example: `localhost` bound for local development
   - Access: `http://localhost:5173`

3. **Using himarket-frontend Service IP**:
   - Accessing via himarket-frontend service IP will reach the "default portal", which is the first portal created
   - Local development environment can access `http://127.0.0.1:5173`

---

### Register as Developer

Developers need to register an account in the portal before they can browse and subscribe to API products.

**Registration Steps:**

1. Access the portal homepage
2. Click the 【Register】button
3. Fill in developer information:
   - Username: Unique developer identifier
   - Password: Set login password
4. Click 【Register】to submit the application

![](https://github.com/user-attachments/assets/c5bb8ab7-c55a-4d19-b20f-99bf65ef655c)

**Registration Approval:**

The status after registration depends on the portal configuration:

- Auto-approval mode: Registration takes effect immediately, can login directly
- Manual approval mode: Registration requires administrator approval
  - Cannot login to the portal before approval
  - Administrator approves in the backend 【Developers】menu

![](https://github.com/user-attachments/assets/4d3743ac-01b3-4770-a1e0-f5a8d0955e4c)

After approval, you can login to the portal

![](https://github.com/user-attachments/assets/47f51797-b92f-4235-8b70-dd96c0d9c51b)

**Notes:**
- Developer accounts are independent for each portal
- Different portals require separate registration

---

### Browse and Subscribe API Products

After registering and logging in, developers can browse API products in the portal and apply for subscriptions.

**Browse API Products:**

1. Login to the developer portal
2. Navigate to 【Models】, 【MCP】, 【Agents】, and other product pages
3. Browse product list:
   - View product name, description, and version
   - Filter and search for products of interest
4. Click on a product card to view details:
   - Product feature introduction
   - API Product usage guide
   - Call example code (if available)
   - Pricing information (if available)

![](https://github.com/user-attachments/assets/14a0fb9c-0b09-46c1-a6f3-5e6ffd29d3e2)

---

### Create Consumer

In Himarket's design:
- Developer: Represents user identity, used for login and management
- Consumer: Represents calling credentials, used for API Product authentication

A Developer can create multiple Consumers for different application scenarios (such as development environment, test environment, production environment). Each Developer is automatically assigned a primary consumer upon registration and supports creating new Consumers.

**Creation Steps:**

1. Login to the developer portal
2. Click on the avatar in the upper right corner to enter 【Consumer Management】

![](https://github.com/user-attachments/assets/6af8fd64-7755-40ec-8abd-3158fae59e8f)

3. Click the 【Create Consumer】button
4. Fill in consumer information:
   - Consumer Name: Used to identify this consumer (e.g., `my-app-prod`)
   - Description: Brief description of purpose (e.g., production environment application)
5. Click 【Submit】

![](https://github.com/user-attachments/assets/c3d6cc15-7b10-4f3c-8a67-f5d0a755594e)

**After Successful Creation:**
- The system will generate a set of credentials (such as API Key, Secret)
- Can manage and delete created consumers in the list

![](https://github.com/user-attachments/assets/cf6522af-46e7-43af-828f-80e7b8cbf6fa)

**Apply for Product Subscription:**

After creating a consumer, you can use the consumer to subscribe to API products:

1. Select the API product to subscribe to
2. Click the 【Subscribe】button on the product details page
3. Select the Consumer to use
4. Click 【Confirm Subscription】

![](https://github.com/user-attachments/assets/1d6dc5d3-8a1c-4dc4-9843-59f79b33b583)

**Subscription Approval:**

- Auto-approval: Takes effect immediately after submission, can directly call API Product
- Manual approval: Needs to wait for administrator approval

**Notes:**
- Subscription status can be viewed in the Consumer details 【Subscription List】
- Can only use the Consumer's credentials to call the corresponding API Product after successful subscription
- Can cancel subscription at any time

---

### AI Playground

Himarket provides an AI Playground for quickly experiencing AI resources, such as chatting with models and combining models with MCP Servers.

**Call Models and MCP:**

1. Login to the developer portal
2. Enter the 【Playground】page

![](https://github.com/user-attachments/assets/f324e384-845e-4201-9ca4-2ebd3ab75350)

3. Select the model product to test, you can chat with the model and optionally combine it with MCP Server during the conversation
4. **Note**:
   - Models and MCP Servers need to be subscribed to for normal access. The Himarket AI Playground uses the Developer's primary consumer by default, which needs to subscribe to the corresponding API Products
   - Developers can configure the primary consumer in the 【Consumer Management】page
5. Operation example:
   - Select model and MCP Server

![](https://github.com/user-attachments/assets/d8b0d691-2dc7-47d7-9b42-c2cf8d43f40c)

   - Specify and subscribe to MCP Server. Quick subscribe uses the primary consumer to execute subscription operations

![](https://github.com/user-attachments/assets/8088532d-b419-4d33-86d2-23c80edbe8ad)

   - Chat with the model

![](https://github.com/user-attachments/assets/a396bc08-6d70-47bd-bf75-807486e2c63a)

---

## Congratulations!

You have successfully completed the Himarket platform setup! Now you can:

- Manage AI resources efficiently through the Admin Console
- Publish standardized API products to developer portals
- Enable developers to self-service browse, subscribe, and call AI capabilities
- Experience the powerful features of Model + MCP in the AI Playground

Thank you for choosing Himarket! We're excited to see what you build with it. 🌟

