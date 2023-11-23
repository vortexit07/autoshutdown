# Auto Shutdown

Auto Shutdown is an application designed to automatically shut down the system before loadshedding begins in order to prevent damage to the system's hardware or data.

## Installation

Download the installer from the releases tab and run

## Getting a License Key
1. Go to the EskomSePush API page on Gumroad or click [here](https://eskomsepush.gumroad.com/l/api)
2. Make sure that the free plan is selected on the right
3. Click "Subscribe" and proceed with the purchase (you will not be charged)
4. On the next screen, copy your license key (should look something like XXXXXXXX-XXXXXXXX-XXXXXXXX-XXXXXXXX)

## Usage

Run Auto Shutdown and input your license key in the dialog box

### Navigating the UI
![UI](https://i.imgur.com/0azYzAp.png)

#### 1: Time selector
Select how long before loadshedding starts that you would like to shut down the system or input a custom value (minutes)

#### 2: Settings
Settings menu

#### 3: Help
Displays "about" section and help menu

#### 4: Kill Switch
Toggle program functionality

##### Green - On

##### Red - Off

#### 5: Area Selection
Displays what area's loadshedding schedule to use (Click to change)

#### 6: Upcoming Events
Displays when the next shutdown time will be for the current day

## Error Codes

| Error Code  | Meaning |
| ------------- | ------------- |
| 403  | Not Authenticated (Token Invalid / Disabled) |
| 404  | Not found  |
| 408  | Request Timeout (try again later) |
| 429  | Too Many Requests (Token quota exceeded) |
| 5xx | Server side issue |


## Contributing

Pull requests are welcome. For major changes, please open an issue first
to discuss what you would like to change.

Please make sure to update tests as appropriate.
