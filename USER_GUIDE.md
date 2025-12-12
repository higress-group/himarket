# HiMarket User Guide

This document is designed for administrators and developers of the HiMarket AI open platform, providing detailed operation guides and best practices.

If you are an **administrator**, this document will guide you through the complete process from importing gateway instances, creating portals, to publishing API products. If you are a **developer**, this document will help you understand how to register an account, subscribe to API products, obtain call credentials, and initiate actual calls.

Through this guide, you can quickly master HiMarket's core functions and achieve efficient management and convenient use of AI capabilities.

## Table of Contents
+ [HiMarket Management Console](#himarket-management-console)
    - [Register Administrator](#register-administrator)
    - [Import Higress Instance](#import-higress-instance)
    - [Create Portal](#create-portal)
    - [Create API Product](#create-api-product)
    - [Link API](#link-api)
    - [Write Usage Guide](#write-usage-guide)
    - [Publish to Portal](#publish-to-portal)
+ [HiMarket Developer Portal](#himarket-developer-portal)
    - [Access Portal](#access-portal)
    - [Register Developer](#register-developer)
    - [Browse and Subscribe to API Products](#browse-and-subscribe-to-api-products)
    - [Create Consumer](#create-consumer)
    - [HiChat](#hichat)

---

## HiMarket Management Console
The HiMarket management console is designed for administrators and operators to manage AI resources, create portals, publish API products, etc.

### Register Administrator
When accessing the HiMarket management console for the first time, you need to register an administrator account.

**Operation Steps:**

1. Access the management console address: `http://localhost:5174` (using local development environment as an example)
2. Click the "Register" button
3. Fill in administrator information:
    - Username
    - Password
4. Click "Initialize" to complete account creation
5. Log in to the management console using the registered account

<div align="center">

![](https://github.com/user-attachments/assets/2caca9f6-5da4-4996-8953-ed6f4647cd12)

</div>

---

### Import Higress Instance
Before starting to create API Products, you need to import a Higress gateway instance as the underlying gateway for AI resources.

**Operation Steps:**

1. Log in to the management console
2. Navigate to [Instance Management] → [Gateway Instances]
3. Click the [Import Gateway Instance] button
4. Select [Higress Gateway]
5. Fill in Higress instance information:
    - Console Address: Higress console address (e.g., `http://demo.higress.io`)
    - Username: Higress console username (e.g., `admin`)
    - Password: Higress console password (e.g., `admin`)
    - Gateway Address: Higress gateway service address (e.g., `http://gateway.higress.io`), MCP Server and Model use domain names for access by default. If no domain is configured, the Gateway address is used
6. Click [Import]

<div align="center">

![](https://github.com/user-attachments/assets/fca47d51-db11-457c-9f06-91342bb1fdfa)

</div>

**Notes:**

+ Supports importing multiple Higress instances
+ After import, you can view and manage them in the instance list

<div align="center">

![](https://github.com/user-attachments/assets/61dda900-6f5f-419b-8b61-34a6fd1da514)

</div>

---

### Create Portal
A Portal is a site for external developers. Administrators can create multiple portals, each with independent configuration and API products.

**Operation Steps:**

1. Navigate to the [Portal] menu
2. Click the [Create Portal] button
3. Fill in basic portal information:
    - Portal Name: e.g., `himarket-demo`
    - Portal Description: Brief description of portal purpose
4. Click [Confirm]

<div align="center">

![](https://github.com/user-attachments/assets/178d4644-eee2-488e-9cac-f76a7cad9a7f)

</div>

**Portal Configuration Description:**

After creating a portal, click the portal card to enter the detailed configuration page, which mainly includes the following functions:

#### 1. Overview
+ Portal basic information: name, description, login method, approval policy, etc.
+ Statistics:
    - Number of published API products
    - Number of registered developers

#### 2. Products (Published API Products)
+ Manage API products published in the current portal
+ View product details
+ Remove or update published products

#### 3. Developers (Developer Management)
+ View and manage developers registered in this portal
+ Approve developer registration applications
+ Manage Consumers associated with developers
+ View developer subscription status

#### 4. Security (Security Settings)
User login configuration:

+ Username/Password Login: Traditional username and password method (enabled by default)
+ OIDC Login: Supports third-party identity authentication (such as enterprise SSO)

Approval process configuration:

+ Developer Registration Approval: Choose whether to automatically approve developer registration applications
    - Automatic Approval: Developer registration takes effect immediately
    - Manual Approval: Requires administrator approval in the backend before use
+ API Product Subscription Approval: Choose whether to automatically approve subscription applications
    - Automatic Approval: Developer subscription requests take effect immediately
    - Manual Approval: Requires administrator approval before calling API Products

#### 5. Domain (Domain Management)
+ The system automatically assigns a default domain for each portal
+ Additional custom domains can be bound (e.g., binding `localhost` for local development)

**Notes:**

+ Domains require self-configuration of DNS resolution or hosts file to point to the himarket-frontend service
+ Portals use multi-tenant design, identifying different portal instances through domains
+ For local development, it's recommended to bind the `localhost` domain for easy testing
+ For production environments, it's recommended to configure real domain names and DNS resolution

---

### Create API Product
API Product is a core concept of HiMarket. It encapsulates underlying AI capabilities (such as MCP Server, Model, Agent) into standardized API products.

**Operation Steps:**

1. Navigate to the [API Products] menu
2. Click the [Create API Product] button
3. Fill in product information:
    - Product Name: e.g., `demo-api`
    - Product Type: Select product type (MCP Server, Model API, Agent API, etc.)
    - Product Description: Brief description of product features and purpose
    - Product Category: Select product category, categories can be used for classification and filtering, supports custom categories
    - Product Icon: Select product icon, can use URL link or upload local file
4. Special Note: Model API type products can configure model parameters, such as Model name, Max Tokens, Temperature, whether to support internet search, etc. Model parameters are used for debugging models in the HiMarket frontend. If missing, it may cause model call failures
5. Click [Confirm]

<div align="center">

![](https://github.com/user-attachments/assets/59f94af9-c28d-4313-b17a-79319bbeda8f)

</div>

6. After successful creation, enter the product details page

<div align="center">

![](https://github.com/user-attachments/assets/f10eb08d-3e4e-4c4f-ae8f-e9af04cf1cdf)

</div>

**Product Status Description:**

Newly created API Products have an initial status of **"Pending Configuration"**. The following operations need to be completed before publishing:

1. **Link API**: Associate the product with actual API resources
2. **Configure Usage Guide**: Write product usage documentation
3. **Publish to Portal**: Publish the product to a specified portal for developer subscription

---

### Link API
After creating an API Product, you need to associate it with actual API resources, establishing a mapping relationship between the product and underlying services.

**Operation Steps:**

1. Enter the API Product details page
2. Click the [Link API] tab
3. Select API resource type:
    - MCP Server: From Higress or AI Gateway (Enterprise Higress) MCP service
    - Model API: From Higress or AI Gateway model service
    - Agent API: From AI Gateway Agent application
4. Select the API resource to link from the list. For example, for Model API, link AI routes on Higress
5. Click [Link]

<div align="center">

![](https://github.com/user-attachments/assets/e5bc4963-c7b9-4bbd-9a0e-55eb06cc91c5)

</div>

7. After successful linking, the API Product status changes to **"Pending Release"**. At this point, the API Product can be published to the Portal. The Link API tab will display API configuration information

<div align="center">

![](https://github.com/user-attachments/assets/845935ee-79c4-45dc-9cd0-e49ab840a505)

</div>

---

### Write Usage Guide
Write detailed usage guides for API Products to help developers quickly understand and use APIs.

**Operation Steps:**

1. Enter the API Product details page
2. Click the [Usage Guide] tab
3. Use the Markdown editor to write usage documentation:
    - API Product Introduction: Features and use cases of the API product
    - Authentication Method: How to obtain and use credentials
    - Request Examples: Provide complete request example code
    - Response Examples: Show API return data format
    - Error Code Description: List common error codes and handling methods
    - Best Practices: Usage recommendations and precautions
4. Supports real-time preview to ensure correct formatting
5. Click [Save]

<div align="center">

![](https://github.com/user-attachments/assets/5a35a3ce-4dd3-4644-a7b7-f4ba64c5a387)

</div>

---

### Publish to Portal
When the API Product configuration is complete, it can be published to specified portals for developer use.

**Operation Steps:**

1. Enter the API Product details page
2. Confirm the product status is **"Pending Release"** (API linking completed)
3. Click the [Publish to Portal] button
4. Select target portal:
    - Select one or more portals from the portal list
    - Can be published to multiple portals simultaneously
5. Click [Publish]

<div align="center">

![](https://github.com/user-attachments/assets/6f09d6aa-08f0-4eb9-aab1-1295236ba986)

</div>

**After Publishing:**

+ The product will be immediately displayed in the portal
+ Developers can browse product details and usage guides
+ Developers can apply to subscribe to the product
+ Administrators can view subscription and call status in the backend

<div align="center">

![](https://github.com/user-attachments/assets/dd43e7f5-d490-45bc-a3b1-1ebdb0df66eb)

</div>

At this point, the complete process of an API Product from creation to publication is complete!

---

## HiMarket Developer Portal
The developer portal is a self-service platform for external developers, where developers can register, browse API products, apply for subscriptions, and initiate calls.

### Access Portal
HiMarket portals use **multi-tenant design**, identifying different portal instances through domain names.

**Access Methods:**

1. **Use Default Domain:**
    - Each portal is automatically assigned a default domain
    - For example: `portal-68ac4564bdb292ee9261ff4a.api.portal.local`
    - Requires DNS resolution or hosts file configuration to point the domain to the himarket-frontend service
2. **Use Custom Domain:**
    - Custom domain bound by administrators in the backend
    - For example, `localhost` bound for local development
    - Access: `http://localhost:5173`
3. **Use himarket-frontend Service IP:**
    - Using the himarket-frontend service IP will access the "Default Portal", which is the first portal created
    - For local development environment, access `http://127.0.0.1:5173`

---

### Register Developer
Developers need to register an account in the portal first to browse and subscribe to API products.

**Registration Steps:**

1. Access the portal homepage
2. Click the [Register] button
3. Fill in developer information:
    - Username: Unique developer identifier
    - Password: Set login password
4. Click [Register] to submit application

<div align="center">

![](https://github.com/user-attachments/assets/c5bb8ab7-c55a-4d19-b20f-99bf65ef655c)

</div>

**Registration Approval:**

The status after registration depends on the portal configuration:

+ Automatic Approval Mode: Registration takes effect immediately, can log in directly
+ Manual Approval Mode: Registration requires administrator approval
    - Cannot log in to portal before approval
    - Administrators approve in the backend [Developers] menu

<div align="center">

![](https://github.com/user-attachments/assets/4d3743ac-01b3-4770-a1e0-f5a8d0955e4c)

</div>

After approval, you can log in to the portal

<div align="center">

![](https://github.com/user-attachments/assets/47f51797-b92f-4235-8b70-dd96c0d9c51b)

</div>

**Notes:**

+ Developer accounts for each portal are independent
+ Need to register separately for different portals

---

### Browse and Subscribe to API Products
After registration and login, developers can browse API products in the portal and apply for subscriptions.

**Browse API Products:**

1. Log in to the developer portal
2. Navigate to [Models], [MCP], [Agents], and other product pages
3. Browse product list:
    - View product names, descriptions, and versions
    - Filter and search for products of interest
4. Click product card to view details:
    - Product feature introduction
    - API Product usage guide
    - Call example code (if available)
    - Pricing information (if available)

<div align="center">

![](https://github.com/user-attachments/assets/14a0fb9c-0b09-46c1-a6f3-5e6ffd29d3e2)

</div>

---

### Create Consumer
In HiMarket's design:

+ Developer: Represents user identity, used for login and management
+ Consumer: Represents call credentials, used for API Product authentication

One Developer can create multiple Consumers for different application scenarios (such as development environment, test environment, production environment). Each Developer is automatically assigned a Consumer after registration and supports creating new Consumers.

**Creation Steps:**

1. Log in to the developer portal
2. Click the avatar in the upper right corner and navigate to [Consumer Management]

<div align="center">

![](https://github.com/user-attachments/assets/6af8fd64-7755-40ec-8abd-3158fae59e8f)

</div>

3. Click the [Add Consumer] button
4. Fill in consumer information:
    - Consumer Name: Used to identify this consumer (e.g., `my-app-prod`)
    - Description: Brief explanation of purpose (e.g., production environment application)
5. Click [Submit]

<div align="center">

![](https://github.com/user-attachments/assets/c3d6cc15-7b10-4f3c-8a67-f5d0a755594e)

</div>

**After Successful Creation:**

+ The system will generate a set of credentials (such as API Key, Secret)
+ You can manage and delete created consumers in the list

<div align="center">

![](https://github.com/user-attachments/assets/cf6522af-46e7-43af-828f-80e7b8cbf6fa)

</div>

**Apply for Product Subscription:**

After creating a consumer, you can use the consumer to subscribe to API products:

1. Select the API product to subscribe to
2. Click the [Subscribe] button on the product details page
3. Select the Consumer to use
4. Click [Confirm Subscription]

<div align="center">

![](https://github.com/user-attachments/assets/1d6dc5d3-8a1c-4dc4-9843-59f79b33b583)

</div>

**Subscription Approval:**

+ Automatic Approval: Takes effect immediately after submission, can directly call API Product
+ Manual Approval: Need to wait for administrator approval

**Description:**

+ Subscription status can be viewed in the [Subscription List] of Consumer details
+ After successful subscription, you can use the Consumer's credentials to call the corresponding API Product
+ You can cancel subscription at any time

---

### HiChat
HiMarket provides the HiChat AI Innovation Center for quickly applying AI resources, such as conversing with models and calling MCP Servers with models.

**Call Models and MCP:**

1. Log in to the developer portal
2. Navigate to the [HiChat] page

<div align="center">

![](https://github.com/user-attachments/assets/f324e384-845e-4201-9ca4-2ebd3ab75350)

</div>

3. Select the model product to test, you can converse with this model, and during the conversation, you can choose to combine with MCP Server
4. Note:
    - Models and MCP Servers need to be subscribed to before they can be accessed normally. HiMarket HiChat uses the Developer's default Consumer by default. You need to use the default Consumer to subscribe to corresponding API Products
    - Developers can configure the default Consumer in the [Consumer Management] page
5. Operation Example:
    - Select model and MCP Server

<div align="center">

![](https://github.com/user-attachments/assets/d8b0d691-2dc7-47d7-9b42-c2cf8d43f40c)

</div>

+ Specify and subscribe to MCP Server. Quick subscription will use the default Consumer to execute subscription operations

<div align="center">

![](https://github.com/user-attachments/assets/8088532d-b419-4d33-86d2-23c80edbe8ad)

</div>

+ Converse with model

<div align="center">

![](https://github.com/user-attachments/assets/a396bc08-6d70-47bd-bf75-807486e2c63a)

</div>

At this point, you have successfully completed the full process experience of the HiMarket platform! Thank you for choosing HiMarket! We look forward to seeing you build excellent applications with it.
